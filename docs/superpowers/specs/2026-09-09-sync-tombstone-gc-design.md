# Sync tombstone GC (issue #321)

Reclaims per user sync tombstones once every device the server knows about has been served them, so
a deleted row stops counting against the per user storage quota forever. Full problem statement and
the two safety constraints this design has to satisfy live in the GitHub issue. This doc is the
implementation design, and section 10 lists every place it departs from the issue's proposed shape.

## 1. Registration is an explicit step, and the ack is derived entirely server side

The issue's original proposal has `pull`'s `device` query param be what upserts `sync_device`. That
alone leaves a gap: `SyncEngine.runSyncCycle` pushes the local outbox before it pulls, so a brand
new device with local dirty data pushes on its very first cycle, before ever pulling. A device that
only ever pushes and never successfully pulls would never appear in `sync_device` at all, making it
invisible to the watermark computation rather than merely bounding it low. That is a strictly worse
failure mode than the abandoned device risk the issue already accepts, because the abandoned device
case is at least visible and recoverable (remove it, see section 4).

**Decision:** registration is a separate, required step that runs first in a sync cycle, gating both
push and pull. No device can push or pull without first being known to `sync_device`.

That gate is an ordering rule on the client, not a server enforced invariant. The server does not
refuse a push or a pull whose device it has never seen: a pull like that simply has no row to write
its ack against, and GC then treats the device as unknown rather than as caught up, which is the
conservative direction. Enforcing it with a `403` would buy nothing and would give a client one more
way to lock itself out of sync entirely.

The register call carries the device's identity and its platform, and nothing else. **The client
stays blind to the versioning system.** It never computes, interprets or reports a revision. The one
revision it handles at all is the pull cursor it already echoes back untouched today. Every value
`sync_device` holds is derived server side.

**The ack is computed by `pull`, and it is not the ceiling.** `pull` regains the issue's `device`
query param, and the server advances that device's `last_acked_revision` to the highest revision the
response it just served actually covers. That is deliberately not the `ceiling` value `pull` already
computes for `nextCursor`, because `nextCursor` is null exactly when every table returned a partial
page, which is to say exactly when the device is caught up. A user whose whole dataset fits one page
therefore never produces a non null ceiling, so a ceiling derived ack would sit at the `0` sentinel
forever and section 6's watermark check would skip that user for good. GC would reclaim nothing for
the common case, which is the shape both the issue and the first draft of this doc had.

The value written is `GREATEST(last_acked_revision, highest revision served)`, and `pull` is the
only writer of that column. Nothing else touches it: not the register call, not push, not the status
check in 3.2.

An earlier draft also folded the request's own `since` into that maximum, on the theory that a
client which advances its cursor only after committing a page is thereby confirming what it applied.
That term was removed because it can never fire. A client's cursor can never exceed the highest
revision it has been served, and that value was already written when the page was served, so `since`
is always less than or equal to what is stored. It would have added a client supplied number to the
input of a hard delete, and made GC's safety depend on `pullAll` writing its cursor after `TreeStore`
commits, in exchange for a branch that is unreachable by construction.

The consequence, stated plainly here and again in section 7: `last_acked_revision` records what the
server has served a device, never what that device has committed. Only the client could know the
latter, and this design deliberately does not ask it.

**A push must never advance the ack, however tempting it looks.** A fresh device with local data
pushes before it has ever pulled, so the server assigns revisions to rows that device demonstrably
already holds, while its ack is still `0`. Crediting those revisions to the pushing device would
look like a free correction and would be a resurrection bug: `last_acked_revision = R` claims the
device has been served *every* row up to `R`, and the rows another device wrote below `R` are not
among the ones it just pushed. GC would then purge tombstones it has never seen. The rows a device
pushes tell you nothing about the rows it is missing, so `pull` stays the only writer.

The visible cost of that is one echo. A fresh device that pushes 20 rows is served those same 20
rows back on its first pull, because its cursor is still `0` and a single cursor cannot skip its own
writes without also skipping everything else below them. Applying them is idempotent, and where the
server resolved a conflict against the pushed row the echo is exactly how that device learns it
lost. The ack lands on the same cycle, since `pull` follows `push` immediately, so the window where
a device that has written 20 rows still reads as `0` is one request wide. GC skipping a user at
watermark `0` is the conservative direction anyway.

## 2. Schema

```sql
CREATE TABLE IF NOT EXISTS sync_device (
  user_id text NOT NULL,
  device_id text NOT NULL,
  platform text NOT NULL,
  last_acked_revision bigint NOT NULL DEFAULT 0,
  last_seen_at timestamptz NOT NULL,
  PRIMARY KEY (user_id, device_id)
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
never breaks an older server's decoding. Client side, one `expect fun currentPlatform(): String`
with an `actual` per source set, mirroring `getPlatformSpecificSettings()`'s pattern in
`StandardAppConfig`.

`last_acked_revision` starts at `0` on insert, the same sentinel `pull`'s `since` already uses for
"nothing seen yet", and is only ever advanced, never rewound.

`last_seen_at` is written by the register call and by nothing else. `pull` and `push` do not touch
it: section 5's heartbeat means a register call lands at least as often as either of them, so a
touch on those paths would be three writes to one row per cycle for no information the register
call did not already carry.

## 3. The device endpoints

### 3.1 Register

`PUT /v1/me/devices/{deviceId}`, authenticated, under the existing `RATE_LIMIT_SYNC_WRITE` tier
alongside push and `/v1/me` deletion. It fires once per sync cycle, the same frequency class as
push.

Request body: `{ "platform": "jvm" }`. Behavior, one upsert:

- New row: `last_acked_revision = 0`, `last_seen_at` = server time, stores `platform`.
- Existing row: `last_seen_at` = server time, `platform` overwritten with the latest reported value.
  A reinstall under the same `originDevice` on a different platform is not expected, and there is no
  reason to make it impossible. `last_acked_revision` is untouched here, since `pull` owns it.

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
newest row a user has is a tombstone and every device has been served it, GC deletes exactly that
row, and the maximum falls below the acks that authorized the delete. Equality would then report a
fully caught up device as out of sync, and it would do so precisely on the users GC works hardest
for.

A `deviceId` with no `sync_device` row under the calling user is a `404`, which is also the answer
for a device belonging to somebody else, since the lookup is always keyed by the caller's own
`user_id`. A malformed `deviceId` is a `400`, as in 3.1.

The maximum is per user and never the `sync_revision` sequence's own value. The sequence is global
and shared across users, so comparing against it would report every device as behind, forever.

Worth being precise about what a new user starts at, since the whole design leans on it. A new
user's *cursor* starts at `0`, and so does `last_acked_revision`. Their *rows* do not: `revision`
comes from the one global `sync_revision` sequence, so a user registering today gets whatever number
that sequence is on, and their own revisions are sparse with large gaps, which `schema.sql` already
calls out as harmless because a cursor only has to be monotonic and comparable, never dense. Two
consequences this design depends on. Comparing one user's revision to another's is meaningless,
hence the per user maximum. And `0` is a safe "nothing seen yet" sentinel in both this check and
section 6's watermark skip, because `nextval` never returns it, so no real row can ever carry
revision `0`.

What this is for: the device management screen in the follow up issue (section 4), which needs to
say how each of a user's devices is doing, and a status badge for the current one. It must not gate
anything in the sync cycle. It also inherits the limit from section 1, which is worth carrying into
whatever label the UI ends up using: a `true` here means the server has served this device
everything, not that the device has committed it, so "up to date as of its last pull" is the honest
phrasing and "Synced" slightly overstates it.

## 4. Removal

`SyncStore` gets internal, non HTTP `listDevicesForTest` and `removeDeviceForTest`, used by the
watermark recompute test and the residual risk test in section 8.

**Decision:** `GET` and `DELETE /v1/me/devices` and the screen that drives them all go to a "device
management" follow up issue, blocked by this one. Nothing in this issue exposes removal over HTTP.

The consequence has to be stated rather than left implied. The abandoned device risk is accepted by
the issue only because removal recovers it, so until that follow up ships, a watermark held down by
a lost or wiped install cannot be unblocked in a deployed build. The failure is benign, GC simply
stops reclaiming for that user, and it is fully recovered later by the follow up rather than needing
a migration or a repair job. The follow up also consumes 3.2's status answer, which is what lets
that screen show the user which device is actually behind.

## 5. Client

### 5.1 The cursor stops resetting to zero

`pullAll` today writes `page.nextCursor` to `SyncCursorStore` unconditionally, so a caught up client
stores `null` and the next cycle starts again from `since = 0`, re downloading and re applying the
whole dataset. That is idempotent and therefore correct, and with no periodic cycle it happens only
when the user writes something. The heartbeat in 5.2 turns it into a full re download every thirty
minutes for every open app, so it has to be fixed here rather than left alone.

`SyncCursorStore` changes from a nullable cursor to a monotonic `Long`, `0` on a fresh install.
`pullAll` resumes from it, writes it only after a page's rows are committed to `TreeStore`, and never
lowers it. A `null` `nextCursor` still terminates the loop, which the monotonic value cannot
express, and an empty page leaves the stored value untouched. No migration is needed, since the
store is `Settings` backed and reads `0` where nothing was written.

The client's view of that number does not change: it is an opaque token to resume from, produced by
the server and echoed back verbatim. Nothing in the ack depends on it. The server reads `since` only
to decide which rows to serve, exactly as it does today, and derives the ack from the rows it served.
This subsection is an efficiency fix that the heartbeat forces, not part of the GC mechanism.

### 5.2 The engine gains a heartbeat, and foreground gets wired

`DefaultSyncEngine` has no periodic cycle. After a successful cycle it goes `IDLE` and wakes only on
`notifyDirty`, `syncNow` or `onAppForeground`, and `onAppForeground` currently has no caller in any
production source set. A device that stops making local writes therefore never pulls again, never
advances its ack, and blocks its user's watermark indefinitely while being perfectly healthy. GC's
whole premise is that registered devices keep pulling, so this is in scope here rather than a
separate concern.

Two changes:

- A `HEARTBEAT` interval that schedules a cycle out of `IDLE`, so an open app keeps acking. Thirty
  minutes to start with. The only constraint is that it stays comfortably shorter than the interval
  between GC runs, since it sets the floor on how stale a watermark can be when GC reads it.
- `onAppForeground` wired per platform, so a backgrounded app acks on return rather than waiting out
  a heartbeat.

`start()` also has to arm it. Today it schedules a timer only when the recovered state is
`SCHEDULED`, so an app relaunched into `IDLE` with a clean outbox runs no cycle at all. It arms the
heartbeat on a recovered `IDLE` too, otherwise the very device this section exists for still never
acks.

`BACKING_OFF`, `PAUSED_NO_AUTH` and `PAUSED_QUOTA_EXCEEDED` keep governing their own timers. The
heartbeat only ever fires out of `IDLE`.

### 5.3 `SyncApiClient` and the cycle

`SyncApiClient` gains `suspend fun registerDevice(accessToken: String, deviceId: String, platform:
String): SyncRegisterOutcome`, following the existing outcome mapping convention (`Ok`,
`Unauthorized`, `RateLimited`, `Error`).

`runSyncCycle` gains a step between acquiring the token and `pushOutbox`:

```
val token = ...
registerDevice(token, deviceIdentity.originDevice, currentPlatform())?.let { return it }
pushOutbox(token, database, apiClient)?.let { return it }
pullAll(token, treeStore, apiClient, cursorStore)?.let { return it }
```

A registration failure aborts the cycle the way an auth failure does today, `CycleOutcome.Transient`
on a network or rate limit failure. Push never runs without a registration having succeeded first in
that cycle.

`SyncApiClient.pull` gains one query param, `device = deviceIdentity.originDevice`, which is the
whole of the client's contribution to the ack. `push` keeps its current signature: `last_seen_at`
comes from the register call, and the ack comes from `pull`, so a param there would carry nothing.

Nothing else on the client changes. There is no new persisted counter, no revision arithmetic, and
no new field to read off a pull response.

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
the same per user advisory lock and find nothing left to delete, so a double run is idempotent rather
than merely tolerable. A future job without that property is the point at which a lock manager, or a
scheduler that ships one, becomes worth buying.

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

Each tick, for every `user_id` present in `sync_device`, which is itself the per user "at least one
registered device" gate, since a user absent from the table is never scanned:

1. `watermark = MIN(last_acked_revision)` for that user.
2. If `watermark == 0`, skip. Either the user has a device that has never pulled anything, or there
   is genuinely nothing to reclaim yet.
3. Otherwise take that user's `acquireUserLock` advisory lock, the same one push and pull use, and
   in one transaction, on each of the five tables in `PER_USER_TABLES`:
   `DELETE FROM <table> WHERE user_id = ? AND is_deleted AND revision <= ?`.

Reusing `acquireUserLock` is what keeps GC from racing a concurrent push for the same user. Either
the push's transaction commits first and GC sees its result, or GC's delete commits first and the
push then sees no stored row, the same as any other fresh key.

A tombstone hard deleted this way leaves no row at all. A later push under the same key inserts
fresh rather than hitting `ON CONFLICT`, correctly indistinguishable from a first ever write.
Section 7 is why no device that could still be holding pre delete data is able to push after this
point.

**Account deletion.** `deleteUser` iterates `PER_USER_TABLES` and must also clear `sync_device`, or
a deleted account leaves device rows behind and a subject signing up again inherits a stale, high
`last_acked_revision`. `sync_device` does not join `PER_USER_TABLES` to get that: GC's own loop uses
the same constant and its `is_deleted` predicate would fail against a table with no such column.
`deleteUser` gets one explicit extra statement instead.

## 7. Why the watermark makes resurrection impossible for a registered device

`pull` returns rows, tombstones included, in revision order, and applying a page applies every row
in it, deletes included. `last_acked_revision = R` means the server has served that device every
change up to and including `R`. GC only deletes tombstones at `revision <= watermark`, and
`watermark = MIN(last_acked_revision)` over every currently registered device, so every registered
device has already been served any tombstone GC is about to purge. No registered device is left that
could still be holding the pre delete version to push.

That argument rests on two properties of the server, and both need stating because a later refactor
could quietly remove either.

**Revision order is commit order, per user.** `push` allocates every `nextval('sync_revision')`
inside `applyBatch`, under `acquireUserLock`, and holds that lock until the transaction commits. So
for one user, a row with a lower revision always committed earlier. Without this, "applied
everything up to `R`" would not imply "missed nothing below `R`".

**A pull page is a consistent snapshot.** `pull` today runs its five per table queries on an
autocommit connection with no lock, so under read committed each statement gets its own snapshot. A
push from the same user committing between the nodes query and the edges query yields a page
carrying the edge at revision 101 without the node at revision 100 from that same push. Acking 101
would then purge a tombstone that page never carried. `pull` therefore moves inside `inTransaction`
and takes `acquireUserLock`, which puts pull, push and GC on one lock and one view, and writes the
ack in that same transaction. This is a fix to existing behavior that this design depends on, not an
addition to it. It also makes `pull` a write path, so it takes the per user lock on every call while
staying on the `RATE_LIMIT_SYNC_READ` budget, which is about request volume rather than about
mutation.

Two residual risks are accepted rather than engineered away.

**A removed device that reconnects.** A device that stops being registered while still holding stale
local data, which the issue already names as accepted and recoverable: "nothing stops a removed
device from reconnecting... treat as a recoverable nuisance". Section 4's open decision is about
whether the recovery path is reachable at all in a deployed build.

**A page is acked when it is served, not when it is applied.** A device that receives a page and
dies before committing it is recorded as having been served those rows, while its own cursor never
advanced, so it re requests a range whose tombstones GC may have purged in the meantime. The result
is a silently stale local row rather than an immediate resurrection, since a pulled row is not dirty
and is never pushed back on its own. It resurrects only if the user later edits that exact row, and
the recovery is the same re delete nuisance as above. Closing it would mean the client confirming a
local commit, which is precisely the versioning knowledge this design keeps off the client. For it
to bite, four things have to coincide: the client dies between receiving a page and committing it,
a GC tick lands before that device comes back, that device is the one holding the minimum, and the
user later edits that exact row.

If that ever proves real, the upgrade is additive and needs no new client knowledge: a second column
holding what was served, with `last_acked_revision` advanced to it only on the device's next pull,
clamped so a client can never claim more than it was given. That is a strictly later decision, and
it is not what this doc specifies.

What this design does close is the gap from section 1, where a push only device never registered at
all and so had no recovery step, and the null ceiling, where the ack never advanced for any user
whose data fits one page.

## 8. Testing

Server, against `SyncStore` directly:

- Watermark: no devices registered, so nothing purged. One device at the `0` sentinel, so nothing
  purged. Every device caught up, so purged. Recomputes correctly after `removeDeviceForTest`.
- Boundaries on the purge predicate: a tombstone at exactly `revision == watermark` is purged, one
  at `watermark + 1` is kept, and a row with `is_deleted = false` at `revision <= watermark` is
  untouched.
- Cross user isolation: user A's watermark never purges a row belonging to user B.
- Quota reclaim, which is the point of the issue: a user at their node cap frees room after GC and a
  push that was refused with `QuotaExceededException` then succeeds.
- `deleteUser` clears that user's `sync_device` rows, and GC then skips the user entirely.

The ack written by `pull`, which is where every value the watermark reads comes from:

- A partial page advances `last_acked_revision` to the highest revision it carried, so a user whose
  whole dataset fits one page advances past `0` on the very first pull. This is the case the ceiling
  derived ack got wrong, so it is the one test that must not be skipped.
- A truncated page advances it to that page's ceiling and no further.
- An empty page leaves it exactly where it was.
- A pull arriving with a `since` below the stored value never rewinds it, which is what an offline
  device replaying an old cursor looks like.
- A pull naming a `device` with no `sync_device` row writes nothing and still serves the page, since
  the registration gate is a client side ordering rule rather than a server enforced invariant.

Register endpoint:

- A first `PUT` creates the row at `last_acked_revision = 0` with the reported platform.
- A second `PUT` refreshes `last_seen_at`, overwrites `platform`, and leaves `last_acked_revision`
  exactly where `pull` left it.
- A `deviceId` that is not a canonical UUID is a `400` and writes nothing.
- `pull` and `push` never write `last_seen_at`.

Sync status endpoint:

- A device whose ack equals the user's maximum revision is synced, one behind it is not, and a user
  with no rows at all is synced.
- The `>=` boundary, which is the case equality would get wrong: every device is served a tombstone
  that is the user's newest row, GC purges it, the user's maximum revision drops below every ack,
  and the endpoint still answers synced.
- An unknown `deviceId`, and a `deviceId` registered under a different user, are both `404`.

Client:

- The cycle ordering: `runSyncCycle` never calls push before registration succeeds, tested on the
  cycle function with a fake `SyncApiClient` whose `registerDevice` fails.
- `SyncCursorStore` is monotonic: it advances only after a page commits, resumes from the stored
  value rather than `0`, is left untouched by an empty page, and a page that fails to apply leaves
  it where it was.
- The heartbeat schedules a cycle out of `IDLE` and does not disturb `BACKING_OFF`,
  `PAUSED_NO_AUTH` or `PAUSED_QUOTA_EXCEEDED`, and `start()` arms it on a recovered `IDLE`.

The GC body is tested as a suspend function on `SyncStore`, called directly. Nothing in the suite
waits on a schedule, and no test starts the krontab coroutine.

The invariant from section 7, and a note on how it is tested. The issue asks for a test that a
replayed stale push is "never accepted as a fresh insert once GC has run past a revision that same
device already acked". No server side assertion can carry that, and deliberately so: `resolve()`
treats an absent local row as a fresh row by design, so a purged tombstone plus a replayed row is
accepted by construction. The invariant is that a device which acked past the delete cannot be
holding such a row in the first place, so it is tested as its own precondition, in two halves:

- Client side: applying a pulled page whose node row has `isDeleted = true` leaves no live local
  copy of that key, so a device that committed a page past a delete has nothing stale to replay.
  This is the half that carries the invariant, since the server's ack records what it served.
- Server side, documenting the accepted residual risk rather than looking for a bug: register two
  devices, one creates and pushes a row, the other deletes it, both ack past the delete, GC purges
  the tombstone, then `removeDeviceForTest` simulates a device that had dropped out before ever
  applying the delete, and its replayed pre delete push is asserted to be accepted as a fresh write.

## 9. Out of scope

- `GET` and `DELETE /v1/me/devices` and the "device management" client screen, all in a follow up
  issue blocked by this one. See section 4 for what that costs in the meantime.
- Per device credential revocation, since auth today is a single user level JWT. The issue already
  scopes this out as a separate, bigger auth feature.
- Any GC cadence or batch size configuration surface. 05:00 UTC nightly is a starting point, not
  something tuned against real traffic. The schedule is a krontab string in code, not an env var.
- Any scheduler infrastructure beyond one schedule. krontab arrives because GC needs a nightly run
  and a hand written loop was not wanted. A task registry, distributed locks and durable misfire
  handling are things to buy when a job actually needs them, not now.
- Backward compatibility for the new register call. The app is not in production, so client and
  server ship together and a newer client against an older server is not a case this design has to
  handle.

## 10. Departures from the issue

- **Registration is its own required step, not a side effect of `pull`.** Section 1: a push only
  device would otherwise never register at all.
- **The ack is the highest revision actually served, not `pull`'s ceiling.** Section 1: a ceiling
  derived ack stays at `0` forever for any user whose data fits one page, which is most users.
  `pull` is its only writer.
- **A sync status route the issue does not mention.** Section 3.2: it answers whether one device has
  been served everything its user has, and the device management follow up needs it.
- **`push` does not gain a `device` query param.** Section 5.3: `last_seen_at` comes from the
  register call and the ack comes from `pull`, so there is nothing left for it to carry. `pull` does
  gain one, as the issue proposed.
- **`pull` becomes transactional and takes the user lock.** Section 7: without a consistent
  snapshot, no ack derived from a page is sound, and the ack is written in that transaction.
- **The client grows a heartbeat and a durable cursor.** Section 5: an idle device never pulled
  again under the current state machine, so it would have blocked its user's watermark forever.
  Neither change teaches the client anything about revisions.
- **GC runs nightly at 05:00 UTC rather than continuously.** Section 6, scheduled by krontab.
- **Both device routes and the screen move to a device management follow up.** Section 4.
- **No global rollout cutoff.** Section 6: the app is not in production, so there is no install and
  no tombstone that predates the registering client.
- **The issue's "never accepted" invariant test is reframed as its precondition.** Section 8: the
  server cannot assert it without contradicting `resolve()`'s deliberate treatment of an absent row.
