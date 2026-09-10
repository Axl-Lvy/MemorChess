# Sync tombstone GC (issue #321)

Reclaims per user sync tombstones once every device the server knows about has committed them, so
a deleted row stops counting against the per user storage quota forever. Full problem statement and
the two safety constraints this design has to satisfy live in the GitHub issue. This doc is the
implementation design, and section 10 lists every place it departs from the issue's proposed shape.

Section 11 records the prior art this design was checked against, since several decisions below are
deliberate departures from what comparable systems do.

## 1. Registration is explicit, and the server owns each device's position

The issue's original proposal has `pull`'s `device` query param be what upserts `sync_device`. That
alone leaves a gap: `SyncEngine.runSyncCycle` pushes the local outbox before it pulls, so a brand
new device with local dirty data pushes on its very first cycle, before ever pulling. A device that
only ever pushes and never successfully pulls would never appear in `sync_device` at all, making it
invisible to the watermark computation rather than merely bounding it low. That is a strictly worse
failure mode than the abandoned device risk the issue already accepts, because the abandoned device
case is at least visible and recoverable.

**Decision:** registration is a separate, required step that runs first in a sync cycle, gating both
push and pull. No device can push or pull without first being known to `sync_device`.

Unlike an earlier draft, the server now enforces that rather than treating it as a client side
ordering rule. A pull or a push naming a `device` with no `sync_device` row under the calling user is
a `400`, and a missing `device` is a `400` too. The reason the enforcement became necessary is that
there is no longer anything sensible to do without the row: 1.1 puts the device's position in it, so
an unknown device has no position to serve from, and 4.1 checks the floor against it, so an unknown
device cannot be told whether it needs to resync. Serving such a caller from `0` would hand a full
dataset to a device the watermark cannot see.

The register call carries the device's identity and its platform, and nothing else. **The client
stays blind to the versioning system.** It never computes, interprets, reports or stores a revision.
The two facts it reports about itself are booleans, described below, and neither is a position.

### 1.1 The server keeps the position, in two columns

The client holds no cursor at all. `sync_device` carries the whole of a device's position:

- `last_served_revision`, the highest revision the server has handed to that device.
- `last_acked_revision`, the highest revision that device has confirmed writing to its local
  database. This is the only column GC reads.

Two columns rather than one, because the server knows what it sent and cannot know what the client
stored. A response can die in transit, and an app can be killed between receiving a page and
committing it to `TreeStore`. Collapsing the two and advancing a single position on serve is what
Kafka calls at most once delivery, and it loses that page for that device permanently. The split is
what makes pull at least once instead, which is the property the client cursor used to provide.

**`ack` is the client's confirmation, and it is a token the server issued.** Every pull response
carries a fresh `pageToken`, generated server side. The client sends back, as `ack`, the `pageToken`
of the page it has just written to its local database, and sends nothing on the first pull of a
cycle because it has committed no page in that cycle yet.

That is the whole of the client's contribution beyond its own device id. It invents nothing, stores
nothing, and interprets nothing. It echoes back one opaque string to mean "this is the page I
wrote".

`pull` does this, in order, in one transaction:

1. If `ack` is present and equals the stored `last_page_token`, set
   `last_acked_revision = last_served_revision`.
2. Serve rows above `last_acked_revision`, with the existing ceiling truncation unchanged.
3. Set `last_served_revision` to the highest revision in this response, or to
   `last_acked_revision` if the response is empty. Generate a fresh `pageToken`, store it as
   `last_page_token`, and return it with the page.

The token rotating on every response is what makes the confirmation idempotent, and it is why there
is no separate replay guard. A replayed request carries an `ack` the first execution already
rotated away from, so it matches nothing, confirms nothing and re serves the same range, which is
exactly what the client would have got had the original response arrived. The same rotation is why
two concurrent cycles cannot over confirm: whichever one acks second is holding a token that no
longer matches.

**Step 4 is a plain assignment, not a maximum**, and that matters. Take a user with rows at
revisions 1 to 1200 and a page size of 500:

| # | `ack` | `last_acked` after | `last_served` after | rows served |
|---|---|---|---|---|
| 1 | none | 0 | 500 | 1 to 500 |
| 2 | token of 1 | 500 | 1000 | 501 to 1000 |
| 3 | token of 2 | 1000 | 1200 | 1001 to 1200 |
| 4 | token of 3 | 1200 | 1200 | none, loop ends |

Now lose request 3's response. The server holds `last_acked = 1000` and `last_served = 1200`, while
the client has committed only through 1000. The client sees a network error, the cycle returns
`Transient`, and the next cycle opens with no `ack`, because it has committed no page in that new
cycle. Nothing is confirmed, rows above 1000 are served again, and the device catches up from there.
One page re sent, none lost. The token it was holding for the lost page was never received, so
there is nothing it could have sent to over confirm.

**Why that step is an assignment and not a `GREATEST`.** In the trace above the two are the same value,
and most of the time they are. They come apart when the retry's ceiling is *lower* than the lost
response's. `pull` computes its ceiling as the minimum last revision across only those tables that
filled their page, so a table that returned a partial page imposes no ceiling at all. Serve a page
where nodes are partial and edges fill at revision 1200, and the ceiling is 1200. Lose that response,
let 200 new node rows arrive, and on the retry the nodes query now fills its page too, at revision
1150, so the ceiling drops to 1150 and the device is served strictly less than it was the first
time. A `GREATEST` would leave `last_served` at 1200, and the next `ack` would confirm
rows 1151 to 1200 that this device has never held. The column means "what I handed this device in
its last response", so it is written as that and nothing else.

`last_acked_revision` stays monotonic regardless, since step 2 sets the two equal and steps 3 and 4
then only raise the second above it.

**A push must never advance either column, however tempting it looks.** A fresh device with local
data pushes before it has ever pulled, so the server assigns revisions to rows that device
demonstrably already holds, while its ack is still `0`. Crediting those revisions to the pushing
device would look like a free correction and would be a resurrection bug: `last_acked_revision = R`
claims the device holds *every* row up to `R`, and the rows another device wrote below `R` are not
among the ones it just pushed. GC would then purge tombstones it has never seen. The rows a device
pushes tell you nothing about the rows it is missing, so `pull` stays the only writer of both.

The visible cost of that is one echo. A fresh device that pushes 20 rows is served those same 20
rows back on its first pull, because its position is still `0` and a single position cannot skip its
own writes without also skipping everything below them. Applying them is idempotent, and where the
server resolved a conflict against the pushed row the echo is exactly how that device learns it
lost.

### 1.2 The honest limit of a client reported boolean

Nothing on the server can tell a client that echoes a `pageToken` before writing the page from one
that echoes it after. A client that acked early would lose pages exactly as a single position design
does. That is a client side obligation, stated here and tested on the client in section 8, not an
invariant the server enforces. It is the same class of contract as the registration ordering above.

It is the only such obligation. The token's rotation covers the rest by construction: a replayed
request cannot confirm twice, and two cycles in flight cannot confirm each other's pages. Single
flight is therefore worth having for other reasons, listed in 5.3, but the acknowledgement no longer
depends on it.

## 2. Schema

```sql
CREATE TABLE IF NOT EXISTS sync_device (
  user_id text NOT NULL,
  device_id text NOT NULL,
  platform text NOT NULL,
  last_acked_revision bigint NOT NULL DEFAULT 0,
  last_served_revision bigint NOT NULL DEFAULT 0,
  last_page_token text,
  removed_at timestamptz,
  last_seen_at timestamptz NOT NULL,
  PRIMARY KEY (user_id, device_id)
);

CREATE TABLE IF NOT EXISTS sync_gc_floor (
  user_id text PRIMARY KEY,
  floor_revision bigint NOT NULL
);
```

No foreign key to an auth table, matching every other per user table in this schema. `user_id` is
an opaque string from the JWT subject, not a local identity.

`device_id` is a UUID, and the client already produces one: `DeviceIdentity.originDevice` is
`Uuid.random().toString()`, persisted per install. The column stays `text` rather than becoming
Postgres `uuid`, so it keeps the type the same value already has in every row table's
`origin_device`. The register route validates the canonical UUID form and answers `400` otherwise.
That validation is the point of the constraint, not the storage: an unvalidated `device_id` lets one
malformed or invented value create a `sync_device` row that never pulls, which holds that user's
watermark down forever with no way to tell it from a real install.

`platform` is a plain string, not a Postgres enum and not a Kotlin `enum class` on the wire, for the
same reason `RejectionCode` already documents: a set of named constants in `:shared`
(`DevicePlatform.ANDROID` / `IOS` / `JVM` / `WASM_JS`), so a future platform added by a newer client
never breaks an older server's decoding. Client side, one `internal expect fun currentPlatform():
String` with an `actual` per source set, mirroring `getPlatformSpecificSettings()`'s pattern in
`StandardAppConfig`.

Both revision columns start at `0` on insert, the same sentinel `pull` already treats as "nothing
seen yet".

`last_page_token` holds the token issued with the most recent pull response. It is rotated on every
pull and compared against the incoming `ack`, and nothing else reads it. One column rather than a
history, because the acknowledgement rides on the next pull rather than arriving on a route of its
own, so the server never has more than one token outstanding per device.

It has to be a column and not a cache, which is worth stating because holding it in memory would
look like a free optimisation. A restart between a page and its acknowledgement would lose the
token, the next `ack` would match nothing, the confirmation would be skipped in silence and that
device's watermark would sit still until a later cycle. A second instance would not see it at all.
It costs nothing where it is, since it is written in the same row update that already writes
`last_served_revision`.

`removed_at` is what makes removal a soft delete. Section 4 covers why it cannot be a hard one.

`last_seen_at` is written by the register call and by nothing else. `pull` and `push` do not touch
it: section 5.2's heartbeat means a register call lands at least as often as either of them, so a
touch on those paths would be three writes to one row per cycle for no information the register
call did not already carry. Nothing reads this column in this issue, which section 4 accepts
explicitly rather than by omission.

`sync_gc_floor` holds, per user, the watermark GC last collected at. It is the highest revision
below which a tombstone may already be gone, and section 4.1 is the one place that reads it.

## 3. The device endpoints

### 3.1 Register

`PUT /v1/me/devices/{deviceId}`, authenticated, under the existing `RATE_LIMIT_SYNC_WRITE` tier
alongside push and `/v1/me` deletion. It fires once per sync cycle, the same frequency class as
push.

Request body: `{ "platform": "jvm", "afterReset": false }`. Behavior, one upsert:

- New row: both revisions `0`, `last_seen_at` = server time, stores `platform`.
- Existing row, not removed: `last_seen_at` = server time, `platform` overwritten with the latest
  reported value. Both revisions untouched, since `pull` owns them.
- Existing row with `removed_at` set: see 4.1, which is the only case that answers anything other
  than `204`.

`afterReset` is the second of the client's two booleans, and it means "I have wiped my synced local
state". It is only ever `true` on the register call that immediately follows a `410`, and its effect
is in 4.1.

Server time comes from the injected `clock: () -> Instant` every other route already takes, not from
SQL `now()`. The skew boundary is testable that way, and `now()` inside a transaction is the
transaction's start time rather than the moment of the write.

A `deviceId` that is not a canonical UUID is a `400` and writes nothing.

Response: `204 No Content`, matching `DELETE /v1/me`'s existing convention.

### 3.2 Sync status

`GET /v1/me/devices/{deviceId}/status`, authenticated, on the `RATE_LIMIT_SYNC_READ` tier. Answers
`{ "synced": true }`.

This is not `/health` or `/ready`. Those two are unauthenticated liveness probes for the deployment
and carry no user context. This one answers a question about one device's position in one user's
data, and it is the only read of `sync_device` this issue ships.

`synced` is `last_acked_revision >= COALESCE(MAX(revision), 0)`, where the maximum runs over that
user's rows in the five tables of `PER_USER_TABLES`. A user with no rows at all is synced. The
comparison is deliberately `>=` and not equality, because GC itself can lower that maximum: when the
newest row a user has is a tombstone and every device has committed it, GC deletes exactly that
row, and the maximum falls below the acks that authorized the delete. Equality would then report a
fully caught up device as out of sync, and it would do so precisely on the users GC works hardest
for.

A `deviceId` with no `sync_device` row under the calling user is a `404`, which is also the answer
for a device belonging to somebody else, since the lookup is always keyed by the caller's own
`user_id`, and for a device whose `removed_at` is set, since from the caller's point of view that
device is no longer registered. A malformed `deviceId` is a `400`, as in 3.1.

The maximum is per user and never the `sync_revision` sequence's own value. The sequence is global
and shared across users, so comparing against it would report every device as behind, forever.

Worth being precise about what a new user starts at, since the whole design leans on it. A new
user's device position starts at `0`. Their *rows* do not: `revision` comes from the one global
`sync_revision` sequence, so a user registering today gets whatever number that sequence is on, and
their own revisions are sparse with large gaps, which `schema.sql` already calls out as harmless
because a position only has to be monotonic and comparable, never dense. Two consequences this
design depends on. Comparing one user's revision to another's is meaningless, hence the per user
maximum. And `0` is a safe "nothing seen yet" sentinel here, in 4.1's floor check and in section 6's
watermark skip, because `nextval` never returns it, so no real row can ever carry revision `0`.

What this is for: the device management screen in the follow up issue (section 4), which needs to
say how each of a user's devices is doing, and a status badge for the current one. It must not gate
anything in the sync cycle. Unlike an earlier draft of this design, a `true` here now means the
device has confirmed committing everything its user has, so "Synced" is honest rather than an
overstatement.

## 4. Removal is a soft delete, and a removed device recovers by resyncing

`SyncStore` gets internal, non HTTP `listDevicesForTest` and `removeDeviceForTest`, used by the
watermark recompute test and the reset tests in section 8.

**Decision:** `GET` and `DELETE /v1/me/devices` and the screen that drives them all go to a "device
management" follow up issue, blocked by this one. Nothing in this issue exposes removal over HTTP.

**Removal sets `removed_at` and never deletes the row.** GC's `MIN` skips rows with `removed_at`
set, so removing a device unblocks that user's watermark immediately, which is the entire point of
the feature. The row itself has to survive, because its `last_acked_revision` is the only thing that
can later tell a returning removed device apart from a brand new install. Both arrive at register
with local state the server cannot see. Only the surviving ack says one of them was last caught up
at revision R while the floor has since moved past R.

This is the same move Postgres made with replication slots: an invalidated slot is marked invalid
and kept, rather than dropped, so the consumer learns what happened when it returns.

`removeDeviceForTest` is therefore a soft removal too. `deleteUser` stays a hard delete and also
clears that user's `sync_device` rows and `sync_gc_floor` row, since after it there is no data left
for a returning device to be missing.

### 4.1 The resync path

`sync_gc_floor.floor_revision` is the highest revision below which a tombstone may already be gone.
A device whose ack is at or above the floor is missing nothing. A device below it may be missing
purged tombstones, and therefore may be holding a live local copy of a key that has since been
deleted.

At register, in this order:

1. **`afterReset` is true**: clear `removed_at`, set both revisions to `0`, answer `204`. This runs
   first and runs unconditionally, whether or not `removed_at` is still set. A `204` from a previous
   reset can be lost in transit, and the client then retries against a row that is already
   reinstated. Ignoring the flag there would leave that device at its old ack having just wiped its
   local database, silently missing everything below it. Zeroing an already zeroed row costs
   nothing, so the unconditional rule is both the safe one and the simple one.
2. **`removed_at` is null**: the ordinary upsert from 3.1. The floor is never consulted for a device
   that is still registered, however far behind it is.
3. **`removed_at` set, and `last_acked_revision >= floor_revision`, or the user has no
   `sync_gc_floor` row**: reinstate. Clear `removed_at`, keep both revisions, answer `204`. Nothing
   was purged that this device needs, so it simply resumes.
4. **`removed_at` set, and `last_acked_revision < floor_revision`**: answer `410 Gone` with
   `ApiErrorCode.RESYNC_REQUIRED`, and write nothing. The device is told to start over.

The reset is confirmed by the client rather than applied on the server's own initiative, and that
ordering is the point. If the server zeroed the position while answering `410`, a client that died
before wiping would pull everything from `0` on top of stale local rows, and a local row for a key
whose tombstone was purged would survive the merge and could be pushed later. That is exactly the
resurrection this whole design exists to prevent. Answering `410` until the client says it has
wiped makes the reset idempotent under any number of failed attempts.

`push` runs the same check and answers the same `410`, which is a belt and braces guard for a client
that skips registration. It is also why section 10 reverses the earlier decision to keep `device`
off the push request.

**What the client wipes:** every row it holds that came from sync, which is the synced tables in
`TreeStore`. Not `Settings`, which sync does not apply today. Not `DeviceIdentity`, since the device
keeps its identity across the reset and the server is counting on that.

**What it loses:** the outbox. Push runs before pull in a cycle, so the outbox is normally empty by
the time a `410` can arrive from pull, but a `410` from push itself catches an outbox with rows in
it, and those are discarded by the wipe. Local edits made on a device after it was removed are lost.
That is the recoverable nuisance the issue already accepts for a removed device, now stated
concretely rather than left as a shape.

### 4.2 What is still not recoverable

Nothing here helps a device whose local data was restored from a backup taken before its current
ack. Its `sync_device` row was never removed, so `removed_at` is null and the floor check never
runs, and the server serves it only rows above an ack that is ahead of what the restore put on disk.
The gap is silent and permanent.

The old client held cursor healed this by accident, since a restored backup restored an old cursor
too. Moving the position server side gives that up, and it is worth naming rather than discovering
later. It is not worth engineering around now: no platform in this app currently backs up the synced
database, and closing it properly means the client attesting to what it holds, which is the
versioning knowledge this design keeps off the client.

## 5. Client

### 5.1 The cursor is deleted outright

`SyncCursorStore`, its Koin binding at `Koin.kt:181`, its `sync.cursor` key and `TestSyncCursorStore`
all go. `pullAll` no longer reads or writes a position.

This replaces, rather than fixes, the problem an earlier draft of this section had. That draft made
the stored cursor monotonic so a caught up client would stop resetting to `0` and re downloading
everything on each heartbeat. It could not work: `nextCursor` is null exactly when every table
returned a partial page, no row carries its revision on the wire, and so a client whose whole
dataset fits one page had no number to store and would have re downloaded everything every thirty
minutes anyway. The same null ceiling that made a ceiling derived ack useless made a client held
cursor useless.

Deleting the cursor also removes a bug that draft would have shipped. `SyncCursorStore` is one
install wide `Settings` key and `signOut()` clears only the token store, so a monotonic cursor would
have survived an account switch. The next account on that install would have pulled from the
previous account's position and silently skipped every row of its own below it, forever. Revisions
come from one global sequence shared by all users, so those rows are not hypothetical. Keying the
position by `(user_id, device_id)` on the server makes this unreachable by construction.

`nextCursor` leaves `SyncPullResponse` entirely. The loop terminates on an empty page instead.

### 5.2 The engine gains a heartbeat, and foreground gets wired

`DefaultSyncEngine` has no periodic cycle. After a successful cycle it goes `IDLE` and wakes only on
`notifyDirty`, `syncNow` or `onAppForeground`, and `onAppForeground` currently has no caller in any
production source set. A device that stops making local writes therefore never pulls again, never
advances its ack, and blocks its user's watermark indefinitely while being perfectly healthy.

That is not an incidental gap. The watermark is the Wuu and Bernstein stability algorithm, and
RR-7506 §4.1 states its liveness precondition directly: a replica "must periodically propagate its
vector clock to update `Vimin` values, possibly by sending empty messages". A heartbeat is what makes
the watermark live, so it belongs in this issue rather than in a separate one.

Three changes:

- A `HEARTBEAT` interval that schedules a cycle out of `IDLE`, so an open app keeps acking. Thirty
  minutes to start with. The only constraint is that it stays comfortably shorter than the interval
  between GC runs, since it sets the floor on how stale a watermark can be when GC reads it.
- `onAppForeground` wired per platform, so a backgrounded app acks on return rather than waiting out
  a heartbeat.
- `start()` arms the heartbeat on a recovered `IDLE`. Today it schedules a timer only when the
  recovered state is `SCHEDULED`, so an app relaunched into `IDLE` with a clean outbox runs no cycle
  at all, which is the very device this section exists for.

The heartbeat only ever fires out of `IDLE`. `BACKING_OFF` keeps its own timer. `PAUSED_NO_AUTH` and
`PAUSED_QUOTA_EXCEEDED` have no timer at all and are left exactly as they are: only `syncNow` or
`onAppForeground` leaves them, by design, and a device parked in either one is a device whose
watermark is legitimately frozen.

### 5.3 `SyncApiClient` and the cycle

`SyncApiClient` gains:

- `suspend fun registerDevice(accessToken: String, deviceId: String, platform: String, afterReset:
  Boolean): SyncRegisterOutcome`, following the existing outcome mapping convention (`Ok`,
  `Unauthorized`, `RateLimited`, `Error`) plus a `ResyncRequired` case for the `410`.
- `pull` stays `GET /v1/sync` on `RATE_LIMIT_SYNC_READ` and gains two query params, `device` and an
  optional `ack`, plus the same `ResyncRequired` case. `SyncPullResponse` gains `pageToken`.

**Pull stays a GET, and the rotating token is what makes that safe.** The argument in 1.2 rests on a
confirmation never being executed twice. HTTP treats `GET` as safe and idempotent, so an intermediary
is entitled to replay one after a lost response with the application none the wiser, and this server
sits behind Cloudflare. A replayed request carrying a bare "yes I committed" flag would confirm a
page the client never received, lose that page, and authorize deleting its tombstones.

`POST` was the alternative, since HTTP grants no such licence to replay one, and it was rejected
because it makes pull look like a write to every caller when the client's view of it is a read.
Making the confirmation self describing is better than either verb: a request that names the page it
confirms is idempotent under any number of replays, deliberate or transparent, and it needs no rule
about which methods may be retried.

Worth being precise about the exposure, because it is not currently reachable from the client. The
engines in use are CIO, Darwin and JS, and `HttpRequestRetry` is installed nowhere, so nothing in
the app replays a request today. The token exists for intermediaries and for the day somebody
installs a retry plugin without reading this section.

An `ack` that matches nothing is not an error. It is silently treated as no confirmation, which is
the conservative direction and is exactly what a replay looks like. Only a malformed `device` is a
`400`.

`GET` also keeps pull on `RATE_LIMIT_SYNC_READ` without the argument having to be made twice: that
tier is about request volume rather than about mutation, which is the same reasoning section 7
already uses for pull taking the per user lock.

`runSyncCycle` gains a step between acquiring the token and `pushOutbox`:

```
val token = ...
registerDevice(token, deviceIdentity.originDevice, currentPlatform(), afterReset = false)
  ?.let { return it }
pushOutbox(token, database, apiClient)?.let { return it }
pullAll(token, treeStore, apiClient, deviceIdentity.originDevice)?.let { return it }
```

A registration failure aborts the cycle the way an auth failure does today, `CycleOutcome.Transient`
on a network or rate limit failure. Push never runs without a registration having succeeded first in
that cycle. A `ResyncRequired` from any of the three runs the wipe from 4.1 and then re registers
with `afterReset = true`, and the cycle ends there rather than continuing on freshly cleared state.

The pull loop holds no persisted state and terminates on an empty page:

```kotlin
var ack: String? = null
while (true) {
  val page = pull(token, deviceId, ack) ?: return outcome
  if (page.isEmpty()) return null
  applyPulledPage(page, treeStore)
  ack = page.pageToken
}
```

The request that returns an empty page is the one that confirms the last page carrying rows, which
is why the loop cannot terminate on the page that carried them. In the steady state, where a
heartbeat finds nothing new, that is still one request, since the first pull returns empty
immediately. The extra round trip is only paid by cycles that actually moved rows.

**One cycle in flight per device, as a stated invariant.** With a client cursor two concurrent cycles
were merely wasteful, and with a rotating `pageToken` they still are: cycle B, holding a token cycle
A has already rotated away from, confirms nothing rather than confirming a page it never saw. So this
is no longer a correctness requirement. It is still worth fixing, because two concurrent cycles push
the same outbox twice and can serve each other's pages into the same `TreeStore`. It is reachable
today, since `runNow()` cancels the timer and calls `launchCycle()` unconditionally, including from
`RUNNING` (`SyncEngine.kt:126-135`), so `syncNow` or `onAppForeground` during a cycle produces
exactly two.

The guard goes in `launchCycle` itself, not in `runNow`: if the state is already `RUNNING`, set
`pendingRetriggerDuringRun` the way `notifyDirty` does and return, so the signal is honoured when the
in flight cycle finishes instead of racing it. Guarding `runNow` alone would not be enough.
`scheduleTimer`'s coroutine calls `launchCycle()` immediately after its `delay` with no suspension
point in between (`SyncEngine.kt:137-143`), so a `cancel()` landing after that delay returns does not
stop the launch, and the timer and `runNow` can each launch one. Putting the guard at the single
place that starts a cycle covers the timer, the heartbeat, `runNow` and any future caller. It is
worth having regardless, since two concurrent cycles also push the same outbox twice.

Nothing else on the client changes. There is no persisted counter, no revision arithmetic, and no
number read off a pull response.

## 6. GC job

Nightly at **05:00 UTC**, stated as a fixed instant rather than as a local hour. That lands at 06:00
in `Europe/Paris` in winter and 07:00 in summer, and the drift is accepted on purpose: no hour is
better than another for reclaiming tombstones, and pinning UTC removes the entire question of zone
rules from the schedule.

**Scheduled by krontab**, `dev.inmo:krontab`, pinned in the version catalog, launched once at boot
in the shape the library's own README uses:

```kotlin
doInfinityTz("0 0 5 * * 0o") { store.collectTombstones() }
```

The library owns the waiting, so nothing here is a hand written `while` loop with a day long
`delay`. Two things about that string are worth a comment in the code, because both are easy to get
wrong:

- **The fields are seconds first**, then minutes, hours, day of month, month, and optionally year,
  offset and week days. It is not a crontab. `"0 4 * * *"` copied from one would mean minute 4 of
  every hour, so the schedule would fire 24 times a day and nobody would notice for a while.
- **`0o` is the offset**, and it says UTC explicitly. krontab also ships `doInfinityLocal`, which
  evaluates against the system zone, exactly what a container's unset `TZ` would then decide for us.
  Naming the offset in the string keeps the schedule independent of the host.

krontab's zone support is a fixed offset and nothing more: it is built on korlibs klock, whose
`DateTimeTz` is a datetime plus an offset in minutes with no tzdata behind it. So no krontab string
can mean "4 AM Paris all year", which is what settled the choice of a UTC hour rather than a local
one. Quartz was the alternative, since `CronScheduleBuilder.inTimeZone` does follow real zone rules,
and it was rejected for what it costs around that one feature: a reflectively instantiated `Job`
class, its collaborator arriving through `SchedulerContext` behind an unchecked cast, a `runBlocking`
bridge on a Quartz worker thread, and non daemon threads that keep the JVM alive unless shutdown is
wired by hand.

**Where it is launched.** Its own `Application.schedulingModule()`, called from `main()` beside
`repertoireModule` and `staticFrontendModule`, never from inside `syncModule`. Every route test
builds `syncModule`, and none of them should acquire a background job as a side effect. The
coroutine runs on a scope cancelled on `ApplicationStopping`.

**What it calls.** One suspend function on `SyncStore` holding the whole GC body. The schedule block
calls it and does nothing else, so the schedule and the reclamation are testable apart, and swapping
krontab for something else later touches five lines.

A missed run, from a restart across 05:00, is harmless: nothing accumulates faster than a day and
the next run reclaims it all.

The SQL lives in `SyncStore`, because `acquireUserLock`, `inTransaction` and the table constants are
all private to it. Only the schedule lives outside. krontab has no notion of running a task on one
instance only, and this job does not need one: a second instance firing at the same 05:00 would take
the same per user advisory lock and find nothing left to delete, so a double run is idempotent
rather than merely tolerable. A future job without that property is the point at which a lock
manager, or a scheduler that ships one, becomes worth buying.

**No rollout cutoff.** The issue asks for a global start date, so that installs predating the
registering client get a chance to appear in `sync_device` before GC trusts a watermark computed
without them. That constraint is dropped, and the reason is the same one that retires backward
compatibility in section 9: the app is not in production, no install predates this feature, and
`applySchema`'s own doc says recreating the database is acceptable until real user data exists, so
there are no pre release tombstones for a cutoff to protect. It would be a delay that guards nothing.

This is the one place where "not in production" is doing load bearing work, so it is worth naming
the condition that brings the cutoff back: if this feature ever reaches real users on a database
that survives the deploy introducing registration, a start date has to come with it, because the
registered device gate below cannot see a device that has not been opened since before the release.

**Each tick**, for every `user_id` present in `sync_device`, which is itself the per user "at least
one registered device" gate, since a user absent from the table is never scanned:

1. `watermark = MIN(last_acked_revision)` over that user's rows **where `removed_at IS NULL`**. A
   user all of whose devices are removed has no watermark and is skipped.
2. If `watermark == 0`, skip. Either the user has a device that has never committed anything, or
   there is genuinely nothing to reclaim yet.
3. Otherwise, **in one transaction per user**, take that user's `acquireUserLock` advisory lock, the
   same one push and pull use, then on each of the five tables in `PER_USER_TABLES`:
   `DELETE FROM <table> WHERE user_id = ? AND is_deleted AND revision <= ?`, and upsert
   `sync_gc_floor` for that user to `watermark`.

One transaction per user, never one spanning the loop. A single transaction over every user would
hold every user's advisory lock at once and block all pushes for as long as GC ran.

Reusing `acquireUserLock` is what keeps GC from racing a concurrent push for the same user. Either
the push's transaction commits first and GC sees its result, or GC's delete commits first and the
push then sees no stored row, the same as any other fresh key.

A tombstone hard deleted this way leaves no row at all. A later push under the same key inserts
fresh rather than hitting `ON CONFLICT`, correctly indistinguishable from a first ever write.
Section 7 is why no device that could still be holding pre delete data is able to push after this
point.

**Account deletion.** `deleteUser` iterates `PER_USER_TABLES` and must also clear `sync_device` and
`sync_gc_floor`, or a deleted account leaves device rows behind and a subject signing up again
inherits a stale, high `last_acked_revision`. Neither table joins `PER_USER_TABLES` to get that:
GC's own loop uses the same constant and its `is_deleted` predicate would fail against a table with
no such column. `deleteUser` gets two explicit extra statements instead.

## 7. Why the watermark makes resurrection impossible

`pull` returns rows, tombstones included, in revision order, and applying a page applies every row
in it, deletes included. `last_acked_revision = R` means that device has confirmed writing every
change up to and including `R` to its local database. GC only deletes tombstones at
`revision <= watermark`, and `watermark = MIN(last_acked_revision)` over every registered, non
removed device, so every such device has already applied any tombstone GC is about to purge. No
device that could still be holding the pre delete version is able to push.

That argument rests on two properties of the server, and both need stating because a later refactor
could quietly remove either.

**Revision order is commit order, per user.** `push` allocates every `nextval('sync_revision')`
inside `applyBatch`, under `acquireUserLock`, and holds that lock until the transaction commits. So
for one user, a row with a lower revision always committed earlier. Without this, "applied
everything up to `R`" would not imply "missed nothing below `R`".

**A pull page is a consistent snapshot.** `pull` today runs its five per table queries on an
autocommit connection with no lock, so under read committed each statement gets its own snapshot. A
push from the same user committing between the nodes query and the edges query yields a page
carrying the edge at revision 101 without the node at revision 100 from that same push. Confirming
101 would then authorize purging a tombstone that page never carried. `pull` therefore moves inside
`inTransaction` and takes `acquireUserLock`, which puts pull, push and GC on one lock and one view,
and writes both position columns in that same transaction. This is a fix to existing behavior that
this design depends on, not an addition to it.

### 7.1 What this closes, and what remains

Closed by the two column position from section 1: a page is now confirmed when it is applied, not
when it is served. An earlier draft accepted the opposite as a residual risk, where a device that
received a page and died before committing it was recorded as having it, and could then find its
tombstones purged. The confirmation token removes that case rather than accepting it.

Closed by section 4: a removed device that reconnects. The issue names this as accepted and
recoverable, "nothing stops a removed device from reconnecting... treat as a recoverable nuisance".
The soft delete plus the floor check turns it into a defined path. The device is told to resync, and
a device that has wiped its local state cannot push a stale row by definition.

Remaining, and accepted: 4.2's restored backup, and 1.2's client that acks a page before writing it.
Both need the client to attest to what it holds, which is the versioning knowledge this design keeps
off it.

### 7.2 The theory this is an instance of

Worth recording, because it is the justification for section 6's one genuinely unbounded behavior.

RR-7506 (Shapiro, Preguiça, Baquero and Zawirski, INRIA RR-7506, HAL `inria-00555588`) splits
distributed GC in two. §4.1 *stability* problems discard metadata once every replica has seen the
concurrent updates, and are solved by exactly the Wuu and Bernstein min over replicas watermark this
design uses. §4.2 *commitment* problems are stronger, and the paper's own example is this design's
subject: "removing tombstones from a 2P-Set (thus allowing deleted elements to be added again) ...
requires an atomic, unanimous agreement between all replicas ... The set of replicas must be known,
and liveness requires that they all be reachable and responsive."

`MIN(last_acked_revision)` is that unanimous agreement, obtained cheaply because one authoritative
server sequences every write instead of a 2PC round between peers. The liveness clause is inherited
along with it, and it is why one unreachable device stalls GC. The paper rates that acceptable:
"When these requirements are not met, GC may block. We consider this to be acceptable, as GC does
not impact correctness (only performance), and the normal operations in the object's interface
remain live."

One difference from the paper's model matters, and it is what makes section 4.1 sound. There, every
replica is a peer holding full state that can originate concurrent updates, which is why unanimity
is required. Here devices are caches and every write goes through the server, so the real condition
is not "every replica has seen the delete" but "no device that could still push a stale row
remains". A device forced into a full resync cannot push a stale row, so evicting one and telling it
to start over preserves the invariant. That is not weakening a commitment protocol, it is evicting a
cache, and it is the same relaxation the paper credits to Leţia et al., who commit by a small stable
core and let the rest reconcile asynchronously.

## 8. Testing

Server, against `SyncStore` directly:

- Watermark: no devices registered, so nothing purged. One device at the `0` sentinel, so nothing
  purged. Every device caught up, so purged. A removed device is excluded from the `MIN`, so its
  stale ack does not hold the watermark down. A user all of whose devices are removed is skipped.
  Recomputes correctly after `removeDeviceForTest`.
- Boundaries on the purge predicate: a tombstone at exactly `revision == watermark` is purged, one
  at `watermark + 1` is kept, and a row with `is_deleted = false` at `revision <= watermark` is
  untouched.
- Cross user isolation: user A's watermark never purges a row belonging to user B.
- Quota reclaim, which is the point of the issue: a user at their node cap frees room after GC and a
  push that was refused with `QuotaExceededException` then succeeds.
- GC writes `sync_gc_floor` to the watermark it collected at, on every tick that gets past the
  `0` skip. A second tick with an unchanged watermark rewrites the same value, and a tick whose
  watermark has advanced raises it.
- `deleteUser` clears that user's `sync_device` and `sync_gc_floor` rows, and GC then skips the user
  entirely.
- One transaction per user: a failure while collecting user B leaves user A's deletions committed.

The two position columns, which is where every value the watermark reads comes from:

- A pull with no `ack` advances `last_served_revision` to the highest revision it carried and
  leaves `last_acked_revision` untouched.
- The next pull, carrying that response's `pageToken` as `ack`, raises `last_acked_revision` to that
  value. A user whose
  whole dataset fits one page therefore advances past `0` on the second request of its first cycle.
  This is the case a ceiling derived acknowledgement got wrong, so it is the one test that must not
  be skipped.
- **The lost response case**, which is the reason there are two columns at all: after a page is
  served, a pull carrying no `ack` re serves everything above `last_acked_revision` and leaves
  `last_acked_revision` exactly where it was. The device loses nothing and confirms nothing.
- **The lowered ceiling case**, which is why `last_served_revision` is assigned rather than raised:
  serve a page whose ceiling comes from a table that filled, add rows to a table that had been
  partial so that it now fills below that ceiling, re serve, and assert `last_served_revision` has
  come *down* to the new ceiling. The following `ack` must then confirm only the lower value. This
  is the one case where a `GREATEST` would silently over confirm.
- A truncated page advances `last_served_revision` to that page's ceiling and no further.
- An empty page leaves `last_served_revision` at `last_acked_revision`, and an `ack` carried by
  that same empty request still confirms the previous page. The empty response still rotates
  `last_page_token`.
- `last_acked_revision` never rewinds across any of the above.
- Neither `push` nor the register call writes either column.
- A pull or a push naming a `device` with no `sync_device` row under the calling user is a `400` and
  writes nothing, as is one naming no device at all. This inverts an earlier draft, where such a
  pull was served and simply went unacknowledged.
- **The replayed pull**, which is what the rotating token exists for: repeating a pull verbatim,
  same `ack`, confirms nothing the second time, re serves the same range, and leaves
  `last_acked_revision` exactly where the first call put it.
- An `ack` matching nothing, an `ack` from two pages ago, and an absent `ack` all confirm nothing and
  are not errors. An `ack` equal to the current `last_page_token` is the only value that confirms.
- Two interleaved pulls, simulating two cycles in flight: the second one's stale token confirms
  nothing, so `last_acked_revision` never runs ahead of what either call was served.

The reset path, with the boundaries CLAUDE.md requires on the floor predicate:

- A removed device with `last_acked_revision` strictly below the floor gets `410 RESYNC_REQUIRED`
  from both register and push, and nothing is written.
- At exactly `last_acked_revision == floor_revision` it is reinstated with `204`, keeping its ack.
  One below the floor is the `410`. This is the boundary the whole path turns on.
- A removed device whose user has no `sync_gc_floor` row is reinstated, never reset.
- A removed device at ack `0` with a floor of `0` is reinstated, since `0 < 0` is false.
- A large floor and a large ack behave the same as small ones, with no overflow or narrowing.
- `afterReset = true` clears `removed_at`, zeroes both columns and answers `204`.
- A repeated `410`, with no `afterReset` in between, is idempotent and still writes nothing, which
  is the client dying mid wipe.
- A device that is *not* removed is never checked against the floor, however far behind it is, and a
  brand new device at ack `0` under a high floor registers normally.

Register endpoint:

- A first `PUT` creates the row at both revisions `0` with the reported platform.
- A second `PUT` refreshes `last_seen_at`, overwrites `platform`, and leaves both revisions exactly
  where `pull` left them.
- A `deviceId` that is not a canonical UUID is a `400` and writes nothing.
- `pull` and `push` never write `last_seen_at`.

Sync status endpoint:

- A device whose ack equals the user's maximum revision is synced, one behind it is not, and a user
  with no rows at all is synced.
- The `>=` boundary, which is the case equality would get wrong: every device commits a tombstone
  that is the user's newest row, GC purges it, the user's maximum revision drops below every ack,
  and the endpoint still answers synced.
- An unknown `deviceId`, a `deviceId` registered under a different user, and a removed one are all
  `404`.

Client:

- The cycle ordering: `runSyncCycle` never calls push before registration succeeds, tested on the
  cycle function with a fake `SyncApiClient` whose `registerDevice` fails.
- The pull loop sends no `ack` on its first request of a cycle and the previous response's
  `pageToken` afterwards, sends it only after `applyPulledPage` returns, terminates on an empty page,
  and a cycle that fails part way through starts the next one with no `ack`. This is the client half
  of the lost response case above.
- One cycle in flight: `syncNow`, `onAppForeground` and an expiring timer during a `RUNNING` cycle
  each set the pending retrigger rather than launching a second cycle. This is a duplicate work fix,
  not an acknowledgement fix, since the token already covers that.
- `SyncApiClient` maps a `410` carrying `ApiErrorCode.RESYNC_REQUIRED` to `ResyncRequired` on each
  of `registerDevice`, `push` and `pull`, and maps a `410` without that code to `Error`. A new
  outcome case needs a propagation test through every call that can produce it.
- `ResyncRequired` from register, from push and from pull each wipe the synced tables in
  `TreeStore`, leave `Settings` and `DeviceIdentity` alone, discard the outbox, re register with
  `afterReset = true`, and end the cycle there.
- The heartbeat schedules a cycle out of `IDLE` and does not disturb `BACKING_OFF`,
  `PAUSED_NO_AUTH` or `PAUSED_QUOTA_EXCEEDED`, and `start()` arms it on a recovered `IDLE`.

The GC body is tested as a suspend function on `SyncStore`, called directly. Nothing in the suite
waits on a schedule, and no test starts the krontab coroutine.

The invariant from section 7, and a note on how it is tested. The issue asks for a test that a
replayed stale push is "never accepted as a fresh insert once GC has run past a revision that same
device already acked". No server side assertion can carry that, and deliberately so: `resolve()`
treats an absent local row as a fresh row by design, so a purged tombstone plus a replayed row is
accepted by construction. The invariant is that a device which committed past the delete cannot be
holding such a row in the first place, so it is tested as its own precondition, in two halves:

- Client side: applying a pulled page whose node row has `isDeleted = true` leaves no live local
  copy of that key, so a device that committed a page past a delete has nothing stale to replay.
  This is the half that carries the invariant.
- Server side: register two devices, one creates and pushes a row, the other deletes it, both
  confirm past the delete, GC purges the tombstone and writes the floor, then `removeDeviceForTest`
  simulates a device that dropped out before applying the delete. Its next register is asserted to
  be the `410`, and its replayed pre delete push is asserted to be refused rather than accepted.
  This is the case that used to be a documented residual risk and is now a handled one.

## 9. Out of scope

- `GET` and `DELETE /v1/me/devices` and the "device management" client screen, all in a follow up
  issue blocked by this one. Section 4 ships the soft delete and the reset path that screen needs,
  so the follow up is UI and routing rather than mechanism.
- Eviction of stale devices by `last_seen_at`. The column is written and nothing reads it, which is
  a deliberate accepted position rather than an oversight: prior art (section 11) universally bounds
  a registration by age, and this design does not, because RR-7506's blocking clause makes the
  failure benign and the per user quota caps its blast radius. The reset path in 4.1 is what makes
  adding the bound later a safe change rather than a risky one, since an evicted device now has a
  defined way back.
- Making `device_seq` causally consistent. RR-7506 §3.2.1 wants a LWW register's timestamps
  "consistent with causal order", implemented as "a per replica counter concatenated with a unique
  replica identifier". `(updated_at, origin_device, device_seq)` does not satisfy that, because
  `updated_at` is a device wall clock. Simply reordering the tuple would not fix it and would make
  things worse, since one device's `device_seq` has no causal relation to another's. The real fix is
  a Lamport clock, raising `device_seq` to `max(local, remote) + 1` when applying a pulled row,
  which touches `DeviceIdentity`'s reservation scheme and the whole apply path. It is a real follow
  up and not part of tombstone GC.
- Per device credential revocation, since auth today is a single user level JWT. The issue already
  scopes this out as a separate, bigger auth feature.
- Any GC cadence or batch size configuration surface. 05:00 UTC nightly is a starting point, not
  something tuned against real traffic. The schedule is a krontab string in code, not an env var.
- Any scheduler infrastructure beyond one schedule. krontab arrives because GC needs a nightly run
  and a hand written loop was not wanted. A task registry, distributed locks and durable misfire
  handling are things to buy when a job actually needs them, not now.
- Observability on a stalled watermark. Prior art has it for good reason (section 11), and section 6
  logging which device holds a user's minimum would be cheap, but nothing consumes server logs today
  and adding a metrics surface is its own piece of work.
- Backward compatibility for the new and changed routes. The app is not in production, so client and
  server ship together and a newer client against an older server is not a case this design has to
  handle. This is what makes moving `pull` to `POST` and dropping `nextCursor` free.

## 10. Departures from the issue

- **Registration is its own required step, not a side effect of `pull`.** Section 1: a push only
  device would otherwise never register at all.
- **The client holds no cursor at all.** Section 5.1: the server keeps each device's position, so
  `SyncCursorStore` is deleted and `nextCursor` leaves the wire.
- **The acknowledgement is what a device confirmed committing, not what it was served.** Section
  1.1: two columns and one client boolean, which is Kafka's at least once discipline rather than its
  at most once. `pull` is the only writer of either column.
- **`pull` keeps its `GET` and gains an `ack`.** Section 5.3: a `GET` carrying a confirmation may
  be replayed by an intermediary, which is exactly the over acknowledgement the design rules out, so
  the confirmation names the page it confirms rather than the verb changing. The token is issued by
  the server on every page and echoed back, so the client invents nothing.
- **`pull` becomes transactional and takes the user lock.** Section 7: without a consistent
  snapshot, no acknowledgement derived from a page is sound.
- **`push` does gain a `device` field after all**, on `SyncPushRequest` rather than as a query
  param, reversing an earlier draft. Section 4.1: a stale device that pushes before pulling is the
  resurrection path, and refusing it with the same `410` closes it.
- **The registration gate is enforced by the server, not just ordered by the client.** Section 1: an
  unknown `device` on pull or push is a `400`, because there is no longer a position to serve from
  or a floor to check against.
- **Removal is a soft delete, and a returning removed device is told to resync.** Sections 4 and
  4.1, and the `sync_gc_floor` table that makes the check possible. The issue accepts this device as
  a recoverable nuisance. It is now a handled case.
- **A sync status route the issue does not mention.** Section 3.2: the device management follow up
  needs it.
- **The client grows a heartbeat.** Section 5.2: an idle device never pulled again under the current
  state machine, and RR-7506 §4.1 makes it a liveness precondition rather than an optimisation.
- **The engine gains a single flight invariant.** Section 5.3: two concurrent cycles are no longer
  merely wasteful once a confirmation is relative rather than self describing.
- **GC runs nightly at 05:00 UTC rather than continuously**, one transaction per user. Section 6,
  scheduled by krontab.
- **Both device routes and the screen move to a device management follow up.** Section 4.
- **No global rollout cutoff.** Section 6: the app is not in production, so there is no install and
  no tombstone that predates the registering client.
- **The issue's "never accepted" invariant test is reframed as its precondition.** Section 8: the
  server cannot assert it without contradicting `resolve()`'s deliberate treatment of an absent row.

## 11. Prior art

Checked against comparable systems, because two decisions here are deliberate minority positions.

**Tombstone retention.** The dominant industry pattern is a fixed window, after which a lagging
consumer must fully resynchronise: Cassandra's `gc_grace_seconds` at 10 days, Kafka's
`delete.retention.ms` at 24 hours, Ditto's tombstone TTL at 7 days, Atlas Device Sync's 30 day
client maximum offline time. CouchDB is the opposite extreme and keeps deleted leaf revisions
forever, with `_purge` admin only and not replicated. This design is in neither family. It gates on
acknowledgement, like CouchDB's clustered purge and like a Postgres replication slot's
`MIN(restart_lsn)`, and unlike both it sets no bound. Postgres is the cautionary case: it started
exactly here and added `max_slot_wal_keep_size` in 13 and `idle_replication_slot_timeout` in 18,
after unbounded pinning filled production disks. Section 9 records why the bound is deferred rather
than rejected.

**Acknowledgement semantics.** Kafka's consumer javadoc is the canonical statement of the choice
this design's section 1.1 makes: with auto commit "records would be considered consumed after they
were returned to the user in `poll`", so you should "manually commit the offsets only after the
corresponding records have been inserted into the database". CloudKit change tokens, Dropbox
cursors and Drive page tokens all advance on the client's next request rather than on serve.

**The resync response.** `410 Gone` for a position the server can no longer honour is the standard
shape. Microsoft Graph returns it with an empty `$deltatoken` as "an indication that the application
must restart with a full synchronization". Kafka throws `OffsetOutOfRangeException` and falls back
to `auto.offset.reset`. Replicache has a `clear` patch op "in case the request cookie is invalid or
not known to the server", and ElectricSQL answers a stale offset with `must-refetch`.

**Device registries.** Nothing surveyed lets a registration live forever. FCM treats one as stale
after a month, collects Android registrations at 270 days, and recommends storing a registration
timestamp. Kafka's `offsets.retention.minutes` is 7 days. Section 9 owns the divergence.

**Observability.** CouchDB's `index_lag_warn_seconds` and Postgres's `inactive_since` and
`wal_status` exist because a stalled watermark is silent until it is not. Section 9 defers this.

**Theory.** RR-7506, cited in 7.2. The stability watermark is Wuu and Bernstein's, and the
commitment framing, its liveness clause and the core subset relaxation are all §4.
