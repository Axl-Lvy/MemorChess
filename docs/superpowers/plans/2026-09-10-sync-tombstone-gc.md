# Sync tombstone GC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reclaim per user sync tombstones once every registered device has confirmed committing them, so a deleted row stops counting against the per user storage quota forever.

**Architecture:** The server keeps each device's sync position in `sync_device`, split into `last_served_revision` (what it handed over) and `last_acked_revision` (what the device confirmed writing). A rotating `pageToken` issued with every pull page is echoed back as `ack` to confirm. A nightly job deletes tombstones below `MIN(last_acked_revision)` per user and records that watermark as a GC floor, and a device that was removed and has fallen below the floor is told to resync from scratch.

**Tech Stack:** Kotlin Multiplatform, Ktor server and client, Postgres via HikariCP and plain JDBC, Testcontainers, Kotest assertions, Koin, krontab for scheduling.

**Spec:** `docs/superpowers/specs/2026-09-09-sync-tombstone-gc-design.md`

## Global Constraints

- **Formatting:** run `./gradlew ktfmtFormat` once at the very end, in a dedicated commit whose only content is the formatting changes. Do not format after every task.
- **Testing:** Kotest assertions, no mocking, AAA pattern. Test through the public API. Never add `public` or `internal` members solely for testing, with the single exception of the `ForTest` helpers the spec names in section 4.
- **Edge cases:** arithmetic and comparisons on external data must be tested at `0`, the lowest non zero value, both sides of every boundary, and a large value. A new sealed subclass needs a propagation test through every consumer in the same PR.
- **Server tests need Docker.** `systemProperty("api.version", "1.40")` is already set in `server/build.gradle.kts`.
- **Database migrations:** the app is not in production. Change the schema freely, no migrations. Never re-enable Room schema export.
- **Visibility:** work down the ladder, `private` then `internal` then `public`. `internal` does not cross Gradle module boundaries, so anything `:shared` exposes to `:composeApp` must be `public` there.
- **KDoc** on all public declarations. One or two lines, symmetric with neighbours, never restating the signature.
- **Commits:** Conventional Commits, `feat(module): ...`. Commit after every task.
- **Worktree:** execute this plan in a dedicated git worktree on branch `feat/sync-tombstone-gc-321`, created via the `superpowers:using-git-worktrees` skill. Never in the main checkout.

**Test commands:**

```sh
./gradlew :server:test --tests "proj.memorchess.axl.server.sync.TestSyncStoreDevices"   # one server class
./gradlew :composeApp:jvmTest --tests "proj.memorchess.axl.core.sync.TestSyncEngineCycle" # one client class
./gradlew :shared:jvmTest                                                                 # shared
```

---

## File Structure

**Created:**
- `shared/src/commonMain/kotlin/proj/memorchess/axl/core/sync/SyncDeviceEnvelopes.kt` — register and status wire types, `DevicePlatform` constants.
- `shared/src/commonTest/kotlin/proj/memorchess/axl/core/sync/TestSyncDeviceEnvelopes.kt`
- `composeApp/src/commonMain/kotlin/proj/memorchess/axl/core/sync/CurrentPlatform.kt` — `internal expect fun currentPlatform(): String` plus four `actual` files.
- `server/src/main/kotlin/proj/memorchess/axl/server/SchedulingModule.kt` — the krontab schedule, nothing else.
- `server/src/test/kotlin/proj/memorchess/axl/server/sync/TestSyncStoreDevices.kt` — registration, reinstatement, the floor boundary.
- `server/src/test/kotlin/proj/memorchess/axl/server/sync/TestSyncStorePosition.kt` — the two position columns and the ack token.
- `server/src/test/kotlin/proj/memorchess/axl/server/sync/TestSyncStoreGc.kt` — the watermark, the purge predicate, the floor.
- `server/src/test/kotlin/proj/memorchess/axl/server/routes/TestDeviceRoutes.kt`

**Modified:**
- `shared/src/commonMain/kotlin/proj/memorchess/axl/core/sync/SyncEnvelopes.kt` — `pageToken` on the pull response, `device` on the push request.
- `shared/src/commonMain/kotlin/proj/memorchess/axl/core/sync/ApiError.kt` — `RESYNC_REQUIRED`.
- `server/src/main/resources/schema.sql` — `sync_device`, `sync_gc_floor`.
- `server/src/main/kotlin/proj/memorchess/axl/server/sync/SyncStore.kt` — the bulk of the work.
- `server/src/main/kotlin/proj/memorchess/axl/server/routes/SyncRoutes.kt` — pull params, push body, two device routes.
- `server/src/main/kotlin/proj/memorchess/axl/server/SyncApplication.kt` — nothing structural, `schedulingModule` is called from `main()`.
- `composeApp/src/commonMain/kotlin/proj/memorchess/axl/core/sync/SyncApiClient.kt`
- `composeApp/src/commonMain/kotlin/proj/memorchess/axl/core/sync/SyncEngine.kt`
- `composeApp/src/commonMain/kotlin/proj/memorchess/axl/Koin.kt`

**Deleted:**
- `composeApp/src/commonMain/kotlin/proj/memorchess/axl/core/sync/SyncCursorStore.kt`
- `composeApp/src/commonTest/kotlin/proj/memorchess/axl/core/sync/TestSyncCursorStore.kt`

---

## Decision this plan locks in

`SyncStore.push` gains a `deviceId` parameter and refuses an unknown device. That means every existing push and pull test has to register a device first. Task 2 adds one shared test helper for that and Task 5 migrates the call sites in the same commit, so the churn lands once rather than spreading across tasks.

---

### Task 1: Shared wire types

**Files:**
- Create: `shared/src/commonMain/kotlin/proj/memorchess/axl/core/sync/SyncDeviceEnvelopes.kt`
- Create: `shared/src/commonTest/kotlin/proj/memorchess/axl/core/sync/TestSyncDeviceEnvelopes.kt`
- Modify: `shared/src/commonMain/kotlin/proj/memorchess/axl/core/sync/SyncEnvelopes.kt`
- Modify: `shared/src/commonMain/kotlin/proj/memorchess/axl/core/sync/ApiError.kt`

**Interfaces:**
- Produces: `SyncDeviceRegisterRequest(platform: String, afterReset: Boolean = false)`, `SyncDeviceStatusResponse(synced: Boolean)`, `object DevicePlatform { ANDROID, IOS, JVM, WASM_JS }`, `ApiErrorCode.RESYNC_REQUIRED`, `SyncPullResponse.pageToken: String`, `SyncPushRequest.device: String`.

- [ ] **Step 1: Write the failing test**

`shared/src/commonTest/kotlin/proj/memorchess/axl/core/sync/TestSyncDeviceEnvelopes.kt`:

```kotlin
package proj.memorchess.axl.core.sync

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.serialization.json.Json

class TestSyncDeviceEnvelopes {

  @Test
  fun aRegisterRequestDefaultsAfterResetToFalse() {
    val decoded = SYNC_JSON.decodeFromString<SyncDeviceRegisterRequest>("""{"platform":"jvm"}""")

    decoded.platform shouldBe DevicePlatform.JVM
    decoded.afterReset shouldBe false
  }

  @Test
  fun aPlatformThisBuildHasNeverHeardOfStillDecodes() {
    val decoded =
      SYNC_JSON.decodeFromString<SyncDeviceRegisterRequest>("""{"platform":"fridge"}""")

    decoded.platform shouldBe "fridge"
  }

  @Test
  fun aStatusResponseRoundTrips() {
    val encoded = SYNC_JSON.encodeToString(SyncDeviceStatusResponse(synced = true))

    SYNC_JSON.decodeFromString<SyncDeviceStatusResponse>(encoded).synced shouldBe true
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest --tests "proj.memorchess.axl.core.sync.TestSyncDeviceEnvelopes"`
Expected: FAIL, compilation error, `SyncDeviceRegisterRequest` is unresolved.

- [ ] **Step 3: Write minimal implementation**

`SyncDeviceEnvelopes.kt`:

```kotlin
package proj.memorchess.axl.core.sync

import kotlinx.serialization.Serializable

/**
 * Registers one device, and reports a completed local wipe.
 *
 * @property platform One of [DevicePlatform], as a plain string.
 * @property afterReset `true` only on the register call that follows a `410`, once the caller has
 *   wiped its synced local state.
 */
@Serializable
data class SyncDeviceRegisterRequest(val platform: String, val afterReset: Boolean = false)

/**
 * Whether one device has confirmed committing everything its user has.
 *
 * @property synced `true` when the device's acknowledgement is at or above the user's highest
 *   revision.
 */
@Serializable data class SyncDeviceStatusResponse(val synced: Boolean)

/**
 * Platforms a device can report.
 *
 * Plain strings for the same reason as [RejectionCode]: a platform added by a newer client must not
 * break an older server's decoding.
 */
object DevicePlatform {
  const val ANDROID: String = "android"
  const val IOS: String = "ios"
  const val JVM: String = "jvm"
  const val WASM_JS: String = "wasmjs"
}
```

In `ApiError.kt`, add to `ApiErrorCode`:

```kotlin
  /**
   * The caller's device was removed and has fallen behind what garbage collection has purged. It
   * must wipe its synced local state and register again reporting the wipe.
   */
  const val RESYNC_REQUIRED: String = "resync_required"
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest --tests "proj.memorchess.axl.core.sync.TestSyncDeviceEnvelopes"`
Expected: PASS.

- [ ] **Step 5: Write the failing test for the changed envelopes**

Append to the same test class:

```kotlin
  @Test
  fun aPullResponseCarriesItsPageToken() {
    val json =
      """{"serverTime":"1970-01-01T00:00:01Z","nextCursor":null,"pageToken":"tok-1",
         "nodes":[],"edges":[],"settings":[]}"""

    SYNC_JSON.decodeFromString<SyncPullResponse>(json).pageToken shouldBe "tok-1"
  }

  @Test
  fun aPushRequestCarriesItsDevice() {
    val request =
      SyncPushRequest(
        nodes = emptyList(),
        edges = emptyList(),
        settings = emptyList(),
        device = "device-1",
      )

    SYNC_JSON.decodeFromString<SyncPushRequest>(SYNC_JSON.encodeToString(request)).device shouldBe
      "device-1"
  }
```

- [ ] **Step 6: Run to verify it fails**

Expected: FAIL, compilation error, `pageToken` and `device` unresolved.

- [ ] **Step 7: Add the two fields**

In `SyncEnvelopes.kt`, add to `SyncPullResponse` after `nextCursor`, and document it:

```kotlin
  /**
   * Opaque token naming this page. The caller sends it back as `ack` on its next pull once these
   * rows are written locally, which is how the server learns the page landed.
   */
  val pageToken: String,
```

Add to `SyncPushRequest`, last, with a default so existing call sites keep compiling:

```kotlin
  /** The pushing device's [proj.memorchess.axl.core.sync] origin id. Empty is refused. */
  val device: String = "",
```

- [ ] **Step 8: Run to verify it passes**

Run: `./gradlew :shared:jvmTest`
Expected: PASS, whole module green.

- [ ] **Step 9: Commit**

```bash
git add shared/src
git commit -m "feat(shared): add device wire types, page token and push device"
```

---

### Task 2: Schema and the device test helper

**Files:**
- Modify: `server/src/main/resources/schema.sql`
- Create: `server/src/test/kotlin/proj/memorchess/axl/server/sync/TestSyncStoreDevices.kt`

**Interfaces:**
- Produces: tables `sync_device` and `sync_gc_floor`; `SyncStore.registerDevice(userId, deviceId, platform, afterReset, now): RegisterOutcome`; `SyncStore.listDevicesForTest(userId): List<DeviceRow>` returning `DeviceRow(deviceId, platform, lastAcked, lastServed, removedAt)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package proj.memorchess.axl.server.sync

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.sync.DevicePlatform
import proj.memorchess.axl.server.db.PostgresTestDb

internal class TestSyncStoreDevices {

  private val store = SyncStore(PostgresTestDb.dataSource())
  private val now = Instant.fromEpochMilliseconds(1_000_000)

  @Test
  fun aFirstRegistrationCreatesTheRowAtZero() = runTest {
    val user = PostgresTestDb.newUserId()

    store.registerDevice(user, "device-a", DevicePlatform.JVM, afterReset = false, now)

    val devices = store.listDevicesForTest(user)
    devices shouldHaveSize 1
    devices.single().lastAcked shouldBe 0L
    devices.single().lastServed shouldBe 0L
    devices.single().platform shouldBe DevicePlatform.JVM
  }
}
```

Add the import `io.kotest.matchers.collections.shouldHaveSize`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :server:test --tests "proj.memorchess.axl.server.sync.TestSyncStoreDevices"`
Expected: FAIL, compilation error, `registerDevice` unresolved.

- [ ] **Step 3: Add the tables**

Append to `server/src/main/resources/schema.sql`:

```sql
-- One row per device the server has ever been told about. The position columns are what tombstone
-- GC reads: last_served_revision is what was handed over, last_acked_revision what the device
-- confirmed writing, and only the second one is safe to collect against.
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

-- The highest revision below which a tombstone may already be gone, per user. Only a device that
-- was removed and has fallen below it needs to resync.
CREATE TABLE IF NOT EXISTS sync_gc_floor (
  user_id text PRIMARY KEY,
  floor_revision bigint NOT NULL
);
```

- [ ] **Step 4: Write the minimal store code**

In `SyncStore.kt`:

```kotlin
  /**
   * Upserts one device, refreshing its last seen time.
   *
   * @return [RegisterOutcome.ResyncRequired] when this device was removed and has fallen below the
   *   user's garbage collection floor, [RegisterOutcome.Ok] otherwise.
   */
  internal suspend fun registerDevice(
    userId: String,
    deviceId: String,
    platform: String,
    afterReset: Boolean,
    serverNow: Instant,
  ): RegisterOutcome =
    inTransaction { connection ->
      connection
        .prepareStatement(
          "INSERT INTO sync_device (user_id, device_id, platform, last_seen_at) " +
            "VALUES (?, ?, ?, ?) ON CONFLICT (user_id, device_id) DO UPDATE SET " +
            "platform = EXCLUDED.platform, last_seen_at = EXCLUDED.last_seen_at"
        )
        .use { statement ->
          statement.setString(1, userId)
          statement.setString(2, deviceId)
          statement.setString(3, platform)
          statement.setTimestamp(4, serverNow.toTimestamp())
          statement.executeUpdate()
        }
      RegisterOutcome.Ok
    }

  /** Every device row for [userId]. Test only, see the design doc section 4. */
  internal suspend fun listDevicesForTest(userId: String): List<DeviceRow> = ...
```

with

```kotlin
/** What [SyncStore.registerDevice] decided. */
internal sealed class RegisterOutcome {
  data object Ok : RegisterOutcome()

  data object ResyncRequired : RegisterOutcome()
}

/** One `sync_device` row, for tests and for the watermark recompute check. */
internal data class DeviceRow(
  val deviceId: String,
  val platform: String,
  val lastAcked: Long,
  val lastServed: Long,
  val removedAt: Instant?,
)
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :server:test --tests "proj.memorchess.axl.server.sync.TestSyncStoreDevices"`
Expected: PASS.

- [ ] **Step 6: Write the failing test for the second registration**

```kotlin
  @Test
  fun asecondRegistrationOverwritesPlatformAndKeepsThePosition() = runTest {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, "device-a", DevicePlatform.JVM, afterReset = false, now)
    store.setPositionForTest(user, "device-a", lastAcked = 40, lastServed = 40)

    store.registerDevice(user, "device-a", DevicePlatform.ANDROID, afterReset = false, now)

    val device = store.listDevicesForTest(user).single()
    device.platform shouldBe DevicePlatform.ANDROID
    device.lastAcked shouldBe 40L
    device.lastServed shouldBe 40L
  }
```

- [ ] **Step 7: Run, watch it fail, add `setPositionForTest`, run again**

Expected first: FAIL, `setPositionForTest` unresolved. Then PASS.

```kotlin
  /** Forces a device's position. Test only, so the floor boundaries are reachable without paging. */
  internal suspend fun setPositionForTest(
    userId: String,
    deviceId: String,
    lastAcked: Long,
    lastServed: Long,
  ) = ...
```

- [ ] **Step 8: Commit**

```bash
git add server/src
git commit -m "feat(server): add sync_device and sync_gc_floor with registration"
```

---

### Task 3: Removal, the GC floor and the resync decision

**Files:**
- Modify: `server/src/main/kotlin/proj/memorchess/axl/server/sync/SyncStore.kt`
- Modify: `server/src/test/kotlin/proj/memorchess/axl/server/sync/TestSyncStoreDevices.kt`

**Interfaces:**
- Consumes: `registerDevice`, `listDevicesForTest`, `setPositionForTest` from Task 2.
- Produces: `SyncStore.removeDeviceForTest(userId, deviceId, at: Instant)`, `SyncStore.setGcFloorForTest(userId, floor: Long)`.

- [ ] **Step 1: Write the failing boundary tests**

All four cases the spec's section 8 names, plus the `0` and large value cases the global constraints require:

```kotlin
  private suspend fun removedDeviceAt(user: String, ack: Long, floor: Long?): RegisterOutcome {
    store.registerDevice(user, "device-a", DevicePlatform.JVM, afterReset = false, now)
    store.setPositionForTest(user, "device-a", lastAcked = ack, lastServed = ack)
    store.removeDeviceForTest(user, "device-a", now)
    if (floor != null) store.setGcFloorForTest(user, floor)
    return store.registerDevice(user, "device-a", DevicePlatform.JVM, afterReset = false, now)
  }

  @Test
  fun aRemovedDeviceBelowTheFloorMustResync() = runTest {
    removedDeviceAt(PostgresTestDb.newUserId(), ack = 99, floor = 100) shouldBe
      RegisterOutcome.ResyncRequired
  }

  @Test
  fun aRemovedDeviceExactlyAtTheFloorIsReinstated() = runTest {
    val user = PostgresTestDb.newUserId()

    removedDeviceAt(user, ack = 100, floor = 100) shouldBe RegisterOutcome.Ok

    val device = store.listDevicesForTest(user).single()
    device.removedAt shouldBe null
    device.lastAcked shouldBe 100L
  }

  @Test
  fun aRemovedDeviceAtZeroUnderAZeroFloorIsReinstated() = runTest {
    removedDeviceAt(PostgresTestDb.newUserId(), ack = 0, floor = 0) shouldBe RegisterOutcome.Ok
  }

  @Test
  fun aRemovedDeviceUnderNoFloorAtAllIsReinstated() = runTest {
    removedDeviceAt(PostgresTestDb.newUserId(), ack = 0, floor = null) shouldBe RegisterOutcome.Ok
  }

  @Test
  fun theFloorComparisonHoldsAtLargeValues() = runTest {
    removedDeviceAt(PostgresTestDb.newUserId(), ack = 9_000_000_000L, floor = 9_000_000_001L) shouldBe
      RegisterOutcome.ResyncRequired
    removedDeviceAt(PostgresTestDb.newUserId(), ack = 9_000_000_001L, floor = 9_000_000_001L) shouldBe
      RegisterOutcome.Ok
  }

  @Test
  fun aDeviceThatWasNeverRemovedIsNeverCheckedAgainstTheFloor() = runTest {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, "device-a", DevicePlatform.JVM, afterReset = false, now)
    store.setPositionForTest(user, "device-a", lastAcked = 1, lastServed = 1)
    store.setGcFloorForTest(user, 5_000)

    store.registerDevice(user, "device-a", DevicePlatform.JVM, afterReset = false, now) shouldBe
      RegisterOutcome.Ok
  }

  @Test
  fun aResyncIsIdempotentUntilTheClientReportsItsWipe() = runTest {
    val user = PostgresTestDb.newUserId()
    removedDeviceAt(user, ack = 99, floor = 100)

    store.registerDevice(user, "device-a", DevicePlatform.JVM, afterReset = false, now) shouldBe
      RegisterOutcome.ResyncRequired

    store.listDevicesForTest(user).single().lastAcked shouldBe 99L
  }

  @Test
  fun reportingTheWipeZeroesThePositionAndReinstates() = runTest {
    val user = PostgresTestDb.newUserId()
    removedDeviceAt(user, ack = 99, floor = 100)

    store.registerDevice(user, "device-a", DevicePlatform.JVM, afterReset = true, now) shouldBe
      RegisterOutcome.Ok

    val device = store.listDevicesForTest(user).single()
    device.lastAcked shouldBe 0L
    device.lastServed shouldBe 0L
    device.removedAt shouldBe null
  }
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :server:test --tests "proj.memorchess.axl.server.sync.TestSyncStoreDevices"`
Expected: FAIL, `removeDeviceForTest` and `setGcFloorForTest` unresolved.

- [ ] **Step 3: Implement the decision, in the order the spec's 4.1 fixes**

Replace `registerDevice`'s body with, inside one transaction:

```kotlin
      val existing = connection.readDevice(userId, deviceId)
      when {
        afterReset -> connection.reinstateAndZero(userId, deviceId, platform, serverNow)
        existing == null || existing.removedAt == null ->
          connection.upsertDevice(userId, deviceId, platform, serverNow)
        existing.lastAcked >= connection.gcFloor(userId) ->
          connection.reinstateKeepingPosition(userId, deviceId, platform, serverNow)
        else -> return@inTransaction RegisterOutcome.ResyncRequired
      }
      RegisterOutcome.Ok
```

`gcFloor` returns `0` when the user has no `sync_gc_floor` row, which makes the "no floor" case fall out of the same comparison rather than needing its own branch.

- [ ] **Step 4: Run to verify they pass**

Expected: PASS, all nine.

- [ ] **Step 5: Commit**

```bash
git add server/src
git commit -m "feat(server): soft delete devices and gate reinstatement on the GC floor"
```

---

### Task 4: The pull position and the ack token

**Files:**
- Modify: `server/src/main/kotlin/proj/memorchess/axl/server/sync/SyncStore.kt:254-290`
- Create: `server/src/test/kotlin/proj/memorchess/axl/server/sync/TestSyncStorePosition.kt`

**Interfaces:**
- Consumes: `registerDevice`, `listDevicesForTest` from Task 2.
- Produces: `SyncStore.pull(userId, deviceId, ack: String?, limit: Int, serverNow: Instant): SyncPullResponse`. The `since` parameter is gone.

- [ ] **Step 1: Write the failing test for a first pull**

```kotlin
  @Test
  fun aFirstPullServesFromZeroAndAcknowledgesNothing() = runTest {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now)
    pushSettings(user, setting("a", "1"))

    val page = store.pull(user, DEVICE, ack = null, limit = 100, now)

    page.settings shouldHaveSize 1
    val device = store.listDevicesForTest(user).single()
    device.lastAcked shouldBe 0L
    device.lastServed shouldBeGreaterThan 0L
    page.pageToken shouldNotBe ""
  }
```

- [ ] **Step 2: Run to verify it fails**

Expected: FAIL, `pull` still takes `since`.

- [ ] **Step 3: Rewrite `pull`**

Move the body inside `inTransaction`, take `acquireUserLock(userId)` first, then:

```kotlin
      val device =
        connection.readDevice(userId, deviceId) ?: throw UnknownDeviceException(deviceId)
      if (device.removedAt != null && device.lastAcked < connection.gcFloor(userId)) {
        throw ResyncRequiredException(deviceId)
      }
      if (ack != null && ack == device.lastPageToken) {
        connection.setAcked(userId, deviceId, device.lastServed)
      }
      val since = connection.readDevice(userId, deviceId)!!.lastAcked
      // ... the five existing queries and the ceiling computation, unchanged, using `since`
      val servedThrough =
        listOf(nodes, edges, settings, repertoires, tags)
          .flatMap { page -> page.map { it.first } }
          .filter { ceiling == null || it <= ceiling }
          .maxOrNull() ?: since
      val pageToken = Uuid.random().toString()
      connection.setServed(userId, deviceId, servedThrough, pageToken)
```

`servedThrough` is a plain assignment, never a maximum. The spec's 1.1 explains why: the ceiling can fall between a lost response and its retry.

- [ ] **Step 4: Run to verify it passes**

Expected: PASS.

- [ ] **Step 5: Write the failing tests for the acknowledgement**

```kotlin
  @Test
  fun echoingThePageTokenAcknowledgesThatPage() = runTest {
    val user = registeredUserWith(setting("a", "1"))
    val first = store.pull(user, DEVICE, ack = null, limit = 100, now)
    val served = store.listDevicesForTest(user).single().lastServed

    store.pull(user, DEVICE, ack = first.pageToken, limit = 100, now)

    store.listDevicesForTest(user).single().lastAcked shouldBe served
  }

  @Test
  fun aReplayedPullAcknowledgesNothingTheSecondTime() = runTest {
    val user = registeredUserWith(setting("a", "1"), setting("b", "2"))
    val first = store.pull(user, DEVICE, ack = null, limit = 1, now)
    store.pull(user, DEVICE, ack = first.pageToken, limit = 1, now)
    val ackedOnce = store.listDevicesForTest(user).single().lastAcked

    store.pull(user, DEVICE, ack = first.pageToken, limit = 1, now)

    store.listDevicesForTest(user).single().lastAcked shouldBe ackedOnce
  }

  @Test
  fun anAckMatchingNothingIsNotAnErrorAndConfirmsNothing() = runTest {
    val user = registeredUserWith(setting("a", "1"))
    store.pull(user, DEVICE, ack = null, limit = 100, now)

    store.pull(user, DEVICE, ack = "not-a-token", limit = 100, now)

    store.listDevicesForTest(user).single().lastAcked shouldBe 0L
  }

  @Test
  fun aLostResponseIsReServedAndConfirmsNothing() = runTest {
    val user = registeredUserWith(setting("a", "1"), setting("b", "2"))
    val first = store.pull(user, DEVICE, ack = null, limit = 1, now)
    store.pull(user, DEVICE, ack = first.pageToken, limit = 1, now) // response "lost"
    val before = store.listDevicesForTest(user).single()

    val retry = store.pull(user, DEVICE, ack = null, limit = 1, now)

    retry.settings shouldHaveSize 1
    store.listDevicesForTest(user).single().lastAcked shouldBe before.lastAcked
  }

  @Test
  fun anEmptyPageLeavesTheServedPositionAtTheAcknowledgedOne() = runTest {
    val user = registeredUserWith(setting("a", "1"))
    val first = store.pull(user, DEVICE, ack = null, limit = 100, now)

    val empty = store.pull(user, DEVICE, ack = first.pageToken, limit = 100, now)

    empty.settings.shouldBeEmpty()
    val device = store.listDevicesForTest(user).single()
    device.lastServed shouldBe device.lastAcked
  }

  @Test
  fun anUnknownDeviceIsRefused() = runTest {
    shouldThrow<UnknownDeviceException> {
      store.pull(PostgresTestDb.newUserId(), "never-registered", null, 100, now)
    }
  }
```

- [ ] **Step 6: Run, watch each fail, implement, run again**

Expected: all PASS.

- [ ] **Step 7: Write the failing test for the lowered ceiling**

This is the one case where a `GREATEST` would silently over confirm. Fill one table's page while another stays partial, then add rows to the partial one so it fills below the first ceiling.

```kotlin
  @Test
  fun aLowerCeilingOnTheRetryLowersTheServedPosition() = runTest {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now)
    pushSettings(user, setting("s1", "1"), setting("s2", "2"))
    store.pull(user, DEVICE, ack = null, limit = 2, now)
    val high = store.listDevicesForTest(user).single().lastServed
    pushNodes(user, node(fen("n1")), node(fen("n2")))

    store.pull(user, DEVICE, ack = null, limit = 2, now)

    store.listDevicesForTest(user).single().lastServed shouldBeLessThan high
  }
```

- [ ] **Step 8: Run, confirm it fails against a `GREATEST` and passes against an assignment**

If the implementation from Step 3 already assigns, write the test, watch it pass, then temporarily change the assignment to `GREATEST(last_served_revision, ?)`, watch the test fail, and change it back. That is the only way to know this test tests anything.

- [ ] **Step 9: Commit**

```bash
git add server/src
git commit -m "feat(server): move the pull position server side behind an ack token"
```

---

### Task 5: Push takes a device, and the existing tests register one

**Files:**
- Modify: `server/src/main/kotlin/proj/memorchess/axl/server/sync/SyncStore.kt:86-135`
- Modify: `server/src/test/kotlin/proj/memorchess/axl/server/sync/TestSyncStorePush.kt`, `TestSyncStorePull.kt`, `TestSyncStoreQuota.kt`, `TestSyncStoreDeleteUser.kt`, `TestDevice.kt`, `SyncTransport.kt`

**Interfaces:**
- Produces: `SyncStore.push(userId, deviceId, request, serverNow)`.

- [ ] **Step 1: Write the failing test**

```kotlin
  @Test
  fun aPushFromAnUnknownDeviceIsRefused() = runTest {
    shouldThrow<UnknownDeviceException> {
      store.push(PostgresTestDb.newUserId(), "never-registered", SyncPushRequest(...), now)
    }
  }

  @Test
  fun aPushFromARemovedDeviceBelowTheFloorIsRefused() = runTest {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now)
    store.setPositionForTest(user, DEVICE, lastAcked = 99, lastServed = 99)
    store.removeDeviceForTest(user, DEVICE, now)
    store.setGcFloorForTest(user, 100)

    shouldThrow<ResyncRequiredException> { store.push(user, DEVICE, SyncPushRequest(...), now) }
  }

  @Test
  fun aPushNeverMovesEitherPositionColumn() = runTest {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now)

    store.push(user, DEVICE, SyncPushRequest(settings = listOf(setting("a", "1")), ...), now)

    val device = store.listDevicesForTest(user).single()
    device.lastAcked shouldBe 0L
    device.lastServed shouldBe 0L
  }
```

- [ ] **Step 2: Run to verify it fails**

Expected: FAIL, `push` does not take a device.

- [ ] **Step 3: Add the parameter and the two checks**

The checks go at the top of `applyBatch`, after `acquireUserLock(userId)` and before the quota check, so they run under the same lock everything else does.

- [ ] **Step 4: Migrate every existing call site**

Add to `PostgresTestDb` a helper that registers a device and returns the user id:

```kotlin
  /** A fresh user with one registered device, which every push and pull now requires. */
  internal suspend fun newRegisteredUser(store: SyncStore, now: Instant): Pair<String, String> {
    val user = newUserId()
    store.registerDevice(user, "device-a", DevicePlatform.JVM, afterReset = false, now)
    return user to "device-a"
  }
```

Update `TestDevice` to register itself in `sync()` before its first push, and `SyncTransport` to carry a device id.

- [ ] **Step 5: Run the whole server suite**

Run: `./gradlew :server:test`
Expected: PASS. This is the churn commit, so nothing else may be failing when it ends.

- [ ] **Step 6: Commit**

```bash
git add server/src
git commit -m "feat(server): require a registered device on push"
```

---

### Task 6: Sync status

**Files:**
- Modify: `server/src/main/kotlin/proj/memorchess/axl/server/sync/SyncStore.kt`
- Modify: `server/src/test/kotlin/proj/memorchess/axl/server/sync/TestSyncStoreDevices.kt`

**Interfaces:**
- Produces: `SyncStore.deviceStatus(userId, deviceId): Boolean?`, `null` when there is no live row.

- [ ] **Step 1: Write the failing tests**

```kotlin
  @Test
  fun aUserWithNoRowsAtAllIsSynced() = runTest {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now)

    store.deviceStatus(user, DEVICE) shouldBe true
  }

  @Test
  fun aDeviceOneRevisionBehindIsNotSynced() = runTest {
    val user = registeredUserWith(setting("a", "1"))

    store.deviceStatus(user, DEVICE) shouldBe false
  }

  @Test
  fun aDeviceAtTheUsersMaximumIsSynced() = runTest {
    val user = registeredUserWith(setting("a", "1"))
    val page = store.pull(user, DEVICE, ack = null, limit = 100, now)
    store.pull(user, DEVICE, ack = page.pageToken, limit = 100, now)

    store.deviceStatus(user, DEVICE) shouldBe true
  }

  @Test
  fun aDeviceStaysSyncedAfterGcLowersTheUsersMaximum() = runTest {
    // The >= boundary. Delete the newest row, let every device ack the tombstone, collect it, and
    // the user's maximum falls below every acknowledgement.
    ...
    store.deviceStatus(user, DEVICE) shouldBe true
  }

  @Test
  fun anUnknownOrRemovedDeviceHasNoStatus() = runTest {
    val user = PostgresTestDb.newUserId()

    store.deviceStatus(user, "never-registered") shouldBe null

    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now)
    store.removeDeviceForTest(user, DEVICE, now)
    store.deviceStatus(user, DEVICE) shouldBe null
  }
```

- [ ] **Step 2: Run to verify they fail, implement, run again**

The comparison is `last_acked_revision >= COALESCE(MAX(revision), 0)` over the five `PER_USER_TABLES`, never against the sequence.

- [ ] **Step 3: Commit**

```bash
git add server/src
git commit -m "feat(server): answer whether one device has committed everything"
```

---

### Task 7: The garbage collector

**Files:**
- Modify: `server/src/main/kotlin/proj/memorchess/axl/server/sync/SyncStore.kt`
- Create: `server/src/test/kotlin/proj/memorchess/axl/server/sync/TestSyncStoreGc.kt`

**Interfaces:**
- Produces: `SyncStore.collectTombstones()`, `SyncStore.gcFloorForTest(userId): Long?`.

- [ ] **Step 1: Write the failing watermark tests**

```kotlin
  @Test
  fun aUserWithNoRegisteredDeviceIsNeverScanned()
  @Test
  fun aDeviceStillAtZeroBlocksTheWholeUser()
  @Test
  fun everyDeviceCaughtUpPurgesTheTombstone()
  @Test
  fun aRemovedDeviceIsExcludedFromTheMinimum()
  @Test
  fun aUserWhoseDevicesAreAllRemovedIsSkipped()
```

- [ ] **Step 2: Write the failing purge boundary tests**

```kotlin
  @Test
  fun aTombstoneExactlyAtTheWatermarkIsPurged()
  @Test
  fun aTombstoneOneAboveTheWatermarkIsKept()
  @Test
  fun aLiveRowBelowTheWatermarkIsUntouched()
  @Test
  fun oneUsersWatermarkNeverPurgesAnothersRow()
```

- [ ] **Step 3: Write the failing quota test, which is the point of the issue**

```kotlin
  @Test
  fun collectingFreesRoomForAPushThatTheQuotaHadRefused() = runTest {
    // Fill to the node cap with rows, delete some, ack the tombstones on every device, assert the
    // next push throws QuotaExceededException, collect, assert the same push now succeeds.
  }
```

- [ ] **Step 4: Write the failing floor test**

```kotlin
  @Test
  fun collectingRecordsTheWatermarkAsTheFloor()
  @Test
  fun aSecondTickWithAnUnchangedWatermarkRewritesTheSameFloor()
```

- [ ] **Step 5: Run them all, watch them fail, implement, run again**

One transaction per user, never one spanning the loop. Each takes `acquireUserLock(userId)`, deletes across `PER_USER_TABLES` with `WHERE user_id = ? AND is_deleted AND revision <= ?`, and upserts `sync_gc_floor`.

- [ ] **Step 6: Commit**

```bash
git add server/src
git commit -m "feat(server): collect tombstones below the per user acknowledgement watermark"
```

---

### Task 8: Account deletion clears the device tables

**Files:**
- Modify: `server/src/main/kotlin/proj/memorchess/axl/server/sync/SyncStore.kt:413-422`
- Modify: `server/src/test/kotlin/proj/memorchess/axl/server/sync/TestSyncStoreDeleteUser.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
  @Test
  fun deletingAUserClearsItsDevicesAndItsGcFloor() = runTest {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, "device-a", DevicePlatform.JVM, afterReset = false, now)
    store.setGcFloorForTest(user, 100)

    store.deleteUser(user)

    store.listDevicesForTest(user).shouldBeEmpty()
    store.gcFloorForTest(user) shouldBe null
  }

  @Test
  fun aCollectedUserIsSkippedAfterDeletion() = runTest {
    // Registering again under the same subject must not inherit a stale, high acknowledgement.
  }
```

- [ ] **Step 2: Run, watch it fail, add the two statements, run again**

Two explicit statements, never a loop over `PER_USER_TABLES`, whose `is_deleted` predicate has no meaning against either table.

- [ ] **Step 3: Commit**

```bash
git add server/src
git commit -m "fix(server): clear device rows and the GC floor on account deletion"
```

---

### Task 9: The HTTP surface

**Files:**
- Modify: `server/src/main/kotlin/proj/memorchess/axl/server/routes/SyncRoutes.kt`
- Create: `server/src/test/kotlin/proj/memorchess/axl/server/routes/TestDeviceRoutes.kt`
- Modify: `server/src/test/kotlin/proj/memorchess/axl/server/routes/TestSyncRoutes.kt`

**Interfaces:**
- Produces: `PUT /v1/me/devices/{deviceId}`, `GET /v1/me/devices/{deviceId}/status`, `GET /v1/sync` with `device` and `ack`, `POST /v1/sync` reading `request.device`.

- [ ] **Step 1: Write the failing route tests**

```kotlin
  @Test fun aFirstRegistrationAnswers204()
  @Test fun aDeviceIdThatIsNotACanonicalUuidAnswers400()
  @Test fun aRemovedDeviceBelowTheFloorAnswers410WithResyncRequired()
  @Test fun statusAnswersSyncedForACaughtUpDevice()
  @Test fun statusAnswers404ForADeviceRegisteredUnderAnotherUser()
  @Test fun aPullWithoutADeviceParamAnswers400()
  @Test fun aPullWithAnUnknownDeviceAnswers400()
  @Test fun aPushWhoseBodyNamesNoDeviceAnswers400()
  @Test fun bothDeviceRoutesRequireAuthentication()
```

The `410` body must carry `ApiErrorCode.RESYNC_REQUIRED`, since the client branches on the code and never on the status alone.

- [ ] **Step 2: Run, watch them fail, add the routes, run again**

Register goes under `rateLimit(RATE_LIMIT_SYNC_WRITE)` beside push. Status goes under `rateLimit(RATE_LIMIT_SYNC_READ)` beside pull. Map `UnknownDeviceException` to `400` and `ResyncRequiredException` to `410` in `installErrorMapping`, and add the propagation test the global constraints require for a new exception type.

- [ ] **Step 3: Commit**

```bash
git add server/src
git commit -m "feat(server): expose device registration and sync status"
```

---

### Task 10: The nightly schedule

**Files:**
- Create: `server/src/main/kotlin/proj/memorchess/axl/server/SchedulingModule.kt`
- Modify: `gradle/libs.versions.toml`, `server/build.gradle.kts`
- Modify: `server/src/main/kotlin/proj/memorchess/axl/server/SyncApplication.kt` (the `main()` wiring only)

- [ ] **Step 1: Add the dependency**

`dev.inmo:krontab`, pinned in the version catalog.

- [ ] **Step 2: Write the schedule**

```kotlin
/**
 * Runs tombstone collection nightly at 05:00 UTC.
 *
 * Its own module, never part of `syncModule`: every route test builds that one, and none of them
 * should acquire a background job as a side effect.
 */
internal fun Application.schedulingModule(store: SyncStore) {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  monitor.subscribe(ApplicationStopping) { scope.cancel() }
  // Seconds first, then minutes and hours. This is not a crontab: "0 4 * * *" copied from one
  // would mean minute 4 of every hour. "0o" names UTC, so the schedule ignores the host zone.
  scope.launch { doInfinityTz("0 0 5 * * 0o") { store.collectTombstones() } }
}
```

- [ ] **Step 3: Call it from `main()`**

Beside `repertoireModule` and `staticFrontendModule`, never from inside `syncModule`.

- [ ] **Step 4: Verify no test starts it**

Run: `./gradlew :server:test`
Expected: PASS, and the suite must not hang. No test starts the krontab coroutine, and the GC body is tested directly as a suspend function.

- [ ] **Step 5: Commit**

```bash
git add gradle server
git commit -m "feat(server): schedule nightly tombstone collection with krontab"
```

---

### Task 11: The client platform name

**Files:**
- Create: `composeApp/src/commonMain/kotlin/proj/memorchess/axl/core/sync/CurrentPlatform.kt` and four `actual` files under `androidMain`, `iosMain`, `jvmMain`, `wasmJsMain`.

- [ ] **Step 1: Write the failing test**

```kotlin
  @Test
  fun theCurrentPlatformIsOneOfTheKnownNames() {
    currentPlatform() shouldBeIn
      listOf(
        DevicePlatform.ANDROID,
        DevicePlatform.IOS,
        DevicePlatform.JVM,
        DevicePlatform.WASM_JS,
      )
  }
```

- [ ] **Step 2: Run, watch it fail, add `internal expect fun currentPlatform(): String` and the four actuals, run again**

Mirror `getPlatformSpecificSettings()`'s pattern in `StandardAppConfig`, which is `internal expect`.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src
git commit -m "feat(app): report the current platform per source set"
```

---

### Task 12: `SyncApiClient`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/proj/memorchess/axl/core/sync/SyncApiClient.kt`
- Modify: `composeApp/src/commonTest/kotlin/proj/memorchess/axl/core/sync/TestSyncApiClient.kt`

**Interfaces:**
- Produces: `registerDevice(accessToken, deviceId, platform, afterReset): SyncRegisterOutcome` with cases `Ok`, `Unauthorized`, `RateLimited`, `ResyncRequired`, `Error`; `pull(accessToken, deviceId, ack, limit)`; `SyncPullOutcome.ResyncRequired` and `SyncPushOutcome.ResyncRequired`.

- [ ] **Step 1: Write the failing tests, one per mapping**

A new sealed case needs a propagation test through every call that can produce it, so all three:

```kotlin
  @Test fun registerMapsA410NamingResyncRequiredToResyncRequired()
  @Test fun pullMapsA410NamingResyncRequiredToResyncRequired()
  @Test fun pushMapsA410NamingResyncRequiredToResyncRequired()
  @Test fun a410WithoutThatCodeIsAnError()
  @Test fun registerSendsThePlatformAndTheAfterResetFlag()
  @Test fun pullSendsTheDeviceAndOmitsAckWhenItIsNull()
```

Use `MockEngine`, as the existing tests in this class already do.

- [ ] **Step 2: Run, watch them fail, implement, run again**

`namesResyncRequired()` mirrors the existing `namesQuotaExceeded()` helper exactly.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src
git commit -m "feat(app): register a device and carry the pull acknowledgement"
```

---

### Task 13: The cycle, the cursor's deletion and the reset

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/proj/memorchess/axl/core/sync/SyncEngine.kt`
- Delete: `composeApp/src/commonMain/kotlin/proj/memorchess/axl/core/sync/SyncCursorStore.kt`, `composeApp/src/commonTest/kotlin/proj/memorchess/axl/core/sync/TestSyncCursorStore.kt`
- Modify: `composeApp/src/commonTest/kotlin/proj/memorchess/axl/core/sync/TestSyncEngineCycle.kt`

- [ ] **Step 1: Write the failing ordering test**

```kotlin
  @Test
  fun theCycleNeverPushesBeforeRegistrationSucceeds() = runTest {
    val calls = mutableListOf<String>()
    val client = FakeSyncApiClient(onRegister = { calls += "register"; SyncRegisterOutcome.Error("no") },
                                   onPush = { calls += "push"; ... })

    runSyncCycle(...) shouldBe CycleOutcome.Transient

    calls shouldBe listOf("register")
  }
```

- [ ] **Step 2: Write the failing pull loop tests**

```kotlin
  @Test
  fun theLoopSendsNoAckOnItsFirstRequestAndTheLastTokenAfterwards()
  @Test
  fun theLoopSendsTheAckOnlyAfterThePageIsApplied()
  @Test
  fun theLoopTerminatesOnAnEmptyPage()
  @Test
  fun aCycleThatFailsPartWayStartsTheNextOneWithNoAck()
```

- [ ] **Step 3: Write the failing reset tests, one per source**

```kotlin
  @Test fun aResyncFromRegisterWipesAndReRegistersReportingTheWipe()
  @Test fun aResyncFromPushWipesAndReRegistersReportingTheWipe()
  @Test fun aResyncFromPullWipesAndReRegistersReportingTheWipe()
  @Test fun aResyncDiscardsTheOutbox()
  @Test fun aResyncLeavesTheDeviceIdentityAlone()
  @Test fun aResyncEndsTheCycleRatherThanContinuingOnClearedState()
```

- [ ] **Step 4: Run them all, watch them fail, implement, run again**

`runSyncCycle` gains the register step first. `pullAll` loses `cursorStore` and gains `deviceId`, and its loop is:

```kotlin
  var ack: String? = null
  while (true) {
    val page = /* pull(token, deviceId, ack) */ ?: return outcome
    if (page.isEmpty()) return null
    applyPulledPage(page, treeStore)
    ack = page.pageToken
  }
```

`SyncPullResponse.isEmpty()` is a small extension in this file, true when all five lists are empty.

- [ ] **Step 5: Delete the cursor store and its test, and drop it from `Koin.kt` and the `SyncEngine` factory**

- [ ] **Step 6: Run the whole client suite**

Run: `./gradlew :composeApp:jvmTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src
git commit -m "feat(app): drop the client cursor and confirm pages by token"
```

---

### Task 14: The heartbeat, single flight and foreground

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/proj/memorchess/axl/core/sync/SyncEngine.kt:82-190`
- Modify: `composeApp/src/commonTest/kotlin/proj/memorchess/axl/core/sync/TestSyncEngineCycle.kt`
- Modify: the four platform entry points that own the app lifecycle, for `onAppForeground`.

- [ ] **Step 1: Write the failing heartbeat tests**

```kotlin
  @Test fun aHeartbeatSchedulesACycleOutOfIdle()
  @Test fun theHeartbeatDoesNotDisturbBackingOff()
  @Test fun theHeartbeatDoesNotDisturbPausedNoAuth()
  @Test fun theHeartbeatDoesNotDisturbPausedQuotaExceeded()
  @Test fun startArmsTheHeartbeatOnARecoveredIdle()
```

- [ ] **Step 2: Write the failing single flight test**

```kotlin
  @Test
  fun aSecondCycleIsNeverLaunchedWhileOneIsRunning() = runTest {
    // syncNow, onAppForeground and an expiring timer during RUNNING each set the pending
    // retrigger instead of launching. Assert runCycle was invoked exactly once.
  }
```

- [ ] **Step 3: Run, watch them fail, implement, run again**

The guard goes in `launchCycle`, not in `runNow`. `scheduleTimer` calls `launchCycle()` right after its `delay` with no suspension point between, so a late `cancel()` does not stop it, and guarding only `runNow` would leave the timer able to launch a second cycle.

- [ ] **Step 4: Wire `onAppForeground` per platform**

Android from `MainActivity`'s lifecycle, JVM from the window focus listener, wasmJs from the `visibilitychange` event, iOS from the scene lifecycle.

- [ ] **Step 5: Commit**

```bash
git add composeApp androidApp
git commit -m "feat(app): keep acknowledging with a heartbeat and one cycle at a time"
```

---

### Task 15: Wiring and formatting

- [ ] **Step 1: Update `Koin.kt`**

Drop `SyncCursorStore`, pass `DeviceIdentity` into the `SyncEngine` factory so the cycle can name its device.

- [ ] **Step 2: Run every suite**

```sh
./gradlew :shared:jvmTest :composeApp:jvmTest :server:test
```

Expected: PASS, output pristine.

- [ ] **Step 3: Format, in its own commit**

```bash
./gradlew ktfmtFormat
git add -A
git commit -m "style: apply ktfmt"
```

- [ ] **Step 4: Full build**

```sh
./gradlew build
```

---

## Self-Review

**Spec coverage.** §1 registration and the position, Tasks 2 and 4. §1.1 the two columns and the token, Task 4. §2 schema, Task 2. §3.1 register route, Task 9. §3.2 status, Tasks 6 and 9. §4 soft delete, Task 3. §4.1 the resync path, Tasks 3, 9, 13. §4.2 accepted, nothing to build. §5.1 cursor deletion, Task 13. §5.2 heartbeat, Task 14. §5.3 client cycle, Tasks 12 to 14. §6 GC and the schedule, Tasks 7 and 10. §7 the lock and the transaction, Task 4. §8 testing, spread across every task. §9 out of scope, nothing to build. §11 prior art, nothing to build.

**Gap found and closed:** §6's account deletion clause had no task, so Task 8 exists.

**Known open question for the executor.** `TreeStore.eraseAll()` wipes positions and moves. Task 13's reset also has to clear repertoires, tags and the outbox, and whether `DatabaseQueryManager.eraseAll()` already covers those is unverified. Task 13 Step 3's `aResyncDiscardsTheOutbox` test is what settles it. If it does not, that task adds the missing clearing rather than widening `eraseAll`'s contract silently.
