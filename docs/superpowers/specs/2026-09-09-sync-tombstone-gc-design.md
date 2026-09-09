# Sync tombstone GC (issue #321)

Reclaims per-user sync tombstones once every device the server knows about has pulled past them,
so a deleted row stops counting against the per-user storage quota forever. Full problem statement
and the two safety constraints this design has to satisfy live in the GitHub issue; this doc is the
implementation design, and departs from the issue's proposed shape in a few places, explained below.

## 1. Why an explicit "register" step, not "pull registers implicitly"

The issue's original proposal has `pull`'s `device` query param be what upserts `sync_device`. That
alone leaves a gap: `SyncEngine.runSyncCycle` pushes the local outbox *before* it pulls (a brand new
device with local dirty data pushes on its very first cycle, before ever pulling). A device that
only ever pushes — never successfully pulls — would never appear in `sync_device` at all, making it
invisible to the watermark computation rather than merely bounding it low. That's a strictly worse
failure mode than the "abandoned device" risk the issue already accepts, because the abandoned-device
case is at least visible and recoverable (remove it, see [4]).

**Decision:** registration is a separate, required step that runs before anything else in a sync
cycle, gating both push and pull. No device can push or pull without first being known to
`sync_device`.

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

No foreign key to an auth table, matching every other per-user table in this schema (`user_id` is
an opaque string from the JWT subject, not a local identity).

`platform` is a plain string, not a Postgres enum or a Kotlin `enum class` on the wire — same
rationale `RejectionCode` already documents: a set of named constants in `:shared`
(`DevicePlatform.ANDROID` / `IOS` / `JVM` / `WASM_JS`), so a future platform added by a newer client
never breaks an older server's decoding. Client side, one `expect fun currentPlatform(): String`
with an `actual` per source set (mirrors `getPlatformSpecificSettings()`'s pattern in
`StandardAppConfig`).

`last_acked_revision` starts at `0` on insert (the same sentinel `pull`'s `since` already uses for
"nothing seen yet") and is only ever advanced, never rewound, by a later write.

## 3. Registration endpoint

`PUT /v1/me/devices/{deviceId}`, authenticated, under the existing `RATE_LIMIT_SYNC_WRITE` tier
(alongside push and `/v1/me` deletion — this fires once per sync cycle, same frequency class).

Request body: `{ "platform": "jvm" }`. Behavior, upsert:

- New row: `last_acked_revision = 0`, `last_seen_at = now()`, stores `platform`.
- Existing row: `last_seen_at = now()`, `platform` overwritten with the latest reported value
  (a reinstall under the same `originDevice` on a different platform is not expected, but there's
  no reason to make it impossible), `last_acked_revision` untouched.

Response: `204 No Content`, matching `DELETE /v1/me`'s existing convention.

`last_seen_at` is refreshed on every server call that names a device, not only registration: `pull`
and `push` both touch it too (see [5]), so it reflects the device's actual last contact rather than
only the start of a cycle.

## 4. Removal — store-level only in this issue

`GET`/`DELETE /v1/me/devices` (list, and the "remove this device" escape hatch for an abandoned
device blocking GC — see the issue's second safety constraint) have no caller yet: no client screen
in this codebase calls them, and none is in scope here.

**Decision:** `SyncStore` gets internal (non-HTTP) `listDevicesForTest`/`removeDeviceForTest`, used
only by this issue's tests (watermark recompute after removal, and the stale-replay invariant test
in [6]). The public `GET`/`DELETE` routes and any client UI are filed as a follow-up issue, blocked
by this one — they're a thin HTTP/UI wrapper around store-level operations this issue already has to
build and test.

## 5. Client: `SyncEngine` and `SyncApiClient`

`SyncApiClient` gains `suspend fun registerDevice(accessToken: String, deviceId: String, platform:
String): SyncRegisterOutcome`, following the existing `pull`/`push` outcome-mapping convention
(`Ok`, `Unauthorized`, `RateLimited`, `Error`).

`runSyncCycle` gains a step between acquiring the token and `pushOutbox`:

```
val token = ...
registerDevice(token, deviceIdentity.originDevice, currentPlatform())?.let { return it }
pushOutbox(token, database, apiClient)?.let { return it }
pullAll(token, treeStore, apiClient, cursorStore)?.let { return it }
```

A registration failure aborts the cycle the same way an auth failure does today
(`CycleOutcome.Transient` on a network/rate-limit failure); push never runs without a registration
having succeeded first in that cycle.

`pull` gains a required `device` query param (`SyncApiClient.pull` passes
`deviceIdentity.originDevice`). Server upserts `last_acked_revision` to the same
exhausted-page/`ceiling` value `pull` already computes for `nextCursor` (again only advancing it)
and touches `last_seen_at`.

`push` also gains a required `device` query param, for the same reason: a device that pushes must
have already registered (the gate from [1]), so `last_seen_at` can be touched unconditionally —
`UPDATE sync_device SET last_seen_at = now() WHERE user_id = ? AND device_id = ?`, in the same
transaction as `applyBatch`. This is a plain `UPDATE`, not an upsert: if the row is somehow absent
(a client bypassing the client-side ordering — see [7]'s note on the trust boundary), it silently
matches zero rows rather than hard-failing the push, consistent with this design being a heuristic
rather than a server-enforced invariant.

## 6. GC job

An in-process coroutine, started in `main()` on its own `CoroutineScope`, ticking every 30 minutes
(a plain constant — no config surface; trivial to change later, and there's no production traffic
pattern yet to size it against).

Each tick, for every `user_id` distinct in `sync_device` (this *is* the per-user "at least one
registered device" gate — a user absent from the table is never scanned):

1. `watermark = MIN(last_acked_revision)` for that user.
2. If `watermark == 0`, skip — either the user has a device that's never pulled (registered via the
   gate in [1] but stuck at the sentinel), or genuinely nothing to reclaim yet.
3. Otherwise, take that user's existing `acquireUserLock` advisory lock (same one `push` already
   uses) and, in one transaction, on each of the 5 per-user tables:
   `DELETE FROM <table> WHERE user_id = ? AND is_deleted AND revision <= ?`.

Reusing `acquireUserLock` is what prevents GC racing a concurrent push for the same user: either the
push's transaction commits first (GC then sees its result) or GC's delete commits first (the push
then sees no stored row, same as any other fresh key).

A tombstone hard-deleted this way leaves no row at all; a later push under the same key inserts
fresh rather than hitting `ON CONFLICT`, correctly indistinguishable from a first-ever write — by
construction, no device that could still be holding pre-delete data can push after this point (see
[7]).

## 7. Why the watermark makes resurrection structurally impossible for a *registered* device

`pull` returns rows, tombstones included, in revision order, and applying a page always applies
every row in it including deletes. A device's `last_acked_revision = R` therefore means it has
already locally applied every change up to and including `R`. GC only deletes tombstones at
`revision <= watermark`, and `watermark = MIN(last_acked_revision)` over every currently registered
device — so every registered device has, by definition, already pulled (and applied) any tombstone
GC is about to purge. There is no registered device left that could still be holding the pre-delete
version to push.

The only way to defeat this is for a device to stop being registered while still holding stale local
data — i.e. removal ([4]), which the issue already names as the accepted, recoverable residual risk
("nothing stops a removed device from reconnecting... treat as a recoverable nuisance"). This design
doesn't try to close that; it only closes the *unregistered-by-construction* gap from [1], which had
no recovery step at all.

## 8. Testing

- Watermark computation, against `SyncStore` directly: no devices registered → nothing purged; one
  device stuck at the sentinel → nothing purged; every device caught up → purge; recomputes
  correctly after `removeDeviceForTest`.
- Registration: PUT creates a new row at revision 0; a second PUT never rewinds
  `last_acked_revision`; `pull` advances it correctly, including the partial-page (no `nextCursor`)
  case.
- The cycle ordering: `runSyncCycle` never calls push before registration succeeds (unit test on the
  cycle function with a fake `SyncApiClient` that fails `registerDevice`).
- The invariant from [7], directly: register two devices, one creates+pushes a row, the other
  deletes it and both pull past the delete, GC runs and purges the tombstone, `removeDeviceForTest`
  simulates the first device dropping out *before* it ever pulled the delete, then that device
  replays its stale pre-delete push — asserted to be accepted as a fresh write (documents the
  accepted residual risk from [7], not a regression test looking for a bug).

## 9. Out of scope

- `GET`/`DELETE /v1/me/devices` HTTP routes and any "manage devices" client screen — follow-up
  issue, blocked-by this one.
- Per-device credential revocation (auth today is a single user-level JWT) — the issue already scopes
  this out as a separate, bigger auth feature.
- Any GC cadence/batch-size configuration surface — the 30 minute constant is a starting point, not
  tuned against real traffic.
