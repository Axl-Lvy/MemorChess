package proj.memorchess.axl.server.sync

import io.kotest.matchers.collections.shouldHaveSize
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

  private companion object {
    const val DEVICE = "11111111-1111-4111-8111-111111111111"
  }
}
