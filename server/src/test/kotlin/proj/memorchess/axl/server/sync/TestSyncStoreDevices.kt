package proj.memorchess.axl.server.sync

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.sync.DevicePlatform
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.server.db.PostgresTestDb

internal class TestSyncStoreDevices {

  private val store = SyncStore(PostgresTestDb.dataSource())
  private val now = Instant.fromEpochMilliseconds(1_000_000)

  @Test
  fun aFirstRegistrationCreatesTheRowAtZero() = runTest {
    val user = PostgresTestDb.newUserId()

    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now)

    val devices = store.listDevicesForTest(user)
    devices shouldHaveSize 1
    devices.single().lastAcked shouldBe 0L
    devices.single().lastServed shouldBe 0L
    devices.single().platform shouldBe DevicePlatform.JVM
    devices.single().removedAt shouldBe null
  }

  @Test
  fun aSecondRegistrationOverwritesPlatformAndKeepsThePosition() = runTest {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now)
    store.setPositionForTest(user, DEVICE, lastAcked = 40, lastServed = 40)

    store.registerDevice(user, DEVICE, DevicePlatform.ANDROID, afterReset = false, now)

    val device = store.listDevicesForTest(user).single()
    device.platform shouldBe DevicePlatform.ANDROID
    device.lastAcked shouldBe 40L
    device.lastServed shouldBe 40L
  }

  /** Registers, positions and removes a device, then registers it again. */
  private suspend fun returningRemovedDevice(
    user: String,
    ack: Long,
    floor: Long?,
    afterReset: Boolean = false,
  ): RegisterOutcome {
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now)
    store.setPositionForTest(user, DEVICE, lastAcked = ack, lastServed = ack)
    store.removeDeviceForTest(user, DEVICE, now)
    if (floor != null) store.setGcFloorForTest(user, floor)
    return store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset, now)
  }

  @Test
  fun aRemovedDeviceBelowTheFloorMustResync() = runTest {
    returningRemovedDevice(PostgresTestDb.newUserId(), ack = 99, floor = 100) shouldBe
      RegisterOutcome.ResyncRequired
  }

  @Test
  fun aRemovedDeviceExactlyAtTheFloorIsReinstated() = runTest {
    val user = PostgresTestDb.newUserId()

    returningRemovedDevice(user, ack = 100, floor = 100) shouldBe RegisterOutcome.Ok

    val device = store.listDevicesForTest(user).single()
    device.removedAt shouldBe null
    device.lastAcked shouldBe 100L
  }

  @Test
  fun aRemovedDeviceAtZeroUnderAZeroFloorIsReinstated() = runTest {
    returningRemovedDevice(PostgresTestDb.newUserId(), ack = 0, floor = 0) shouldBe
      RegisterOutcome.Ok
  }

  @Test
  fun aRemovedDeviceUnderNoFloorAtAllIsReinstated() = runTest {
    returningRemovedDevice(PostgresTestDb.newUserId(), ack = 0, floor = null) shouldBe
      RegisterOutcome.Ok
  }

  @Test
  fun theFloorComparisonHoldsAtLargeValues() = runTest {
    returningRemovedDevice(
      PostgresTestDb.newUserId(),
      ack = 9_000_000_000L,
      floor = 9_000_000_001L,
    ) shouldBe RegisterOutcome.ResyncRequired

    returningRemovedDevice(
      PostgresTestDb.newUserId(),
      ack = 9_000_000_001L,
      floor = 9_000_000_001L,
    ) shouldBe RegisterOutcome.Ok
  }

  @Test
  fun aDeviceThatWasNeverRemovedIsNeverCheckedAgainstTheFloor() = runTest {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now)
    store.setPositionForTest(user, DEVICE, lastAcked = 1, lastServed = 1)
    store.setGcFloorForTest(user, 5_000)

    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now) shouldBe
      RegisterOutcome.Ok
  }

  @Test
  fun aResyncIsIdempotentUntilTheClientReportsItsWipe() = runTest {
    val user = PostgresTestDb.newUserId()
    returningRemovedDevice(user, ack = 99, floor = 100)

    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now) shouldBe
      RegisterOutcome.ResyncRequired

    store.listDevicesForTest(user).single().lastAcked shouldBe 99L
  }

  @Test
  fun reportingTheWipeZeroesThePositionAndReinstates() = runTest {
    val user = PostgresTestDb.newUserId()
    returningRemovedDevice(user, ack = 99, floor = 100)

    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = true, now) shouldBe
      RegisterOutcome.Ok

    val device = store.listDevicesForTest(user).single()
    device.lastAcked shouldBe 0L
    device.lastServed shouldBe 0L
    device.removedAt shouldBe null
  }

  @Test
  fun reportingTheWipeOnAnAlreadyReinstatedRowStillZeroesIt() = runTest {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now)
    store.setPositionForTest(user, DEVICE, lastAcked = 40, lastServed = 40)

    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = true, now) shouldBe
      RegisterOutcome.Ok

    store.listDevicesForTest(user).single().lastAcked shouldBe 0L
  }

  @Test
  fun aUserWithNoRowsAtAllIsSynced() = runTest {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now)

    store.deviceStatus(user, DEVICE) shouldBe true
  }

  @Test
  fun aDeviceThatHasNotCommittedTheUsersRowsIsNotSynced() = runTest {
    val user = userWithOneSetting()

    store.deviceStatus(user, DEVICE) shouldBe false
  }

  @Test
  fun aDeviceThatHasCommittedEverythingIsSynced() = runTest {
    val user = userWithOneSetting()
    val page = store.pull(user, DEVICE, ack = null, limit = 100, now)
    store.pull(user, DEVICE, ack = page.pageToken, limit = 100, now)

    store.deviceStatus(user, DEVICE) shouldBe true
  }

  @Test
  fun aDeviceOneRevisionBehindIsNotSynced() = runTest {
    val user = userWithOneSetting()
    val highest = store.listDevicesForTest(user).single().let { _ -> highestRevision(user) }
    store.setPositionForTest(user, DEVICE, lastAcked = highest - 1, lastServed = highest)

    store.deviceStatus(user, DEVICE) shouldBe false
  }

  @Test
  fun aDeviceStaysSyncedAfterCollectionLowersTheUsersMaximum() = runTest {
    // The >= boundary, which equality would get wrong. The user's newest row is a tombstone every
    // device has committed, so collecting it drops the maximum below every acknowledgement.
    val user = userWithOneSetting()
    store.push(
      user,
      DEVICE,
      SyncPushRequest(
        emptyList(),
        emptyList(),
        listOf(setting("a", "1", seq = 2).copy(isDeleted = true)),
      ),
      now,
    )
    val page = store.pull(user, DEVICE, ack = null, limit = 100, now)
    store.pull(user, DEVICE, ack = page.pageToken, limit = 100, now)
    store.deviceStatus(user, DEVICE) shouldBe true

    store.collectTombstones()

    store.deviceStatus(user, DEVICE) shouldBe true
  }

  @Test
  fun anUnknownOrRemovedDeviceHasNoStatus() = runTest {
    val user = PostgresTestDb.newUserId()

    store.deviceStatus(user, DEVICE) shouldBe null

    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now)
    store.removeDeviceForTest(user, DEVICE, now)
    store.deviceStatus(user, DEVICE) shouldBe null
  }

  @Test
  fun aDeviceRegisteredUnderAnotherUserHasNoStatus() = runTest {
    val mine = PostgresTestDb.newUserId()
    val theirs = PostgresTestDb.newUserId()
    store.registerDevice(theirs, DEVICE, DevicePlatform.JVM, afterReset = false, now)

    store.deviceStatus(mine, DEVICE) shouldBe null
  }

  private fun setting(key: String, value: String, seq: Long = 1) =
    SettingSyncRow(
      key = key,
      value = value,
      isDeleted = false,
      updatedAt = now,
      originDevice = DEVICE,
      deviceSeq = seq,
    )

  private suspend fun userWithOneSetting(): String {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now)
    store.push(
      user,
      DEVICE,
      SyncPushRequest(emptyList(), emptyList(), listOf(setting("a", "1"))),
      now,
    )
    return user
  }

  private suspend fun highestRevision(user: String): Long =
    store.push(user, DEVICE, SyncPushRequest(emptyList(), emptyList(), emptyList()), now).let {
      store.listDevicesForTest(user).single().lastServed.coerceAtLeast(1)
    }

  private companion object {
    const val DEVICE = "11111111-1111-4111-8111-111111111111"
  }
}
