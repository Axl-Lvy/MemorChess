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

  private companion object {
    const val DEVICE = "11111111-1111-4111-8111-111111111111"
  }
}
