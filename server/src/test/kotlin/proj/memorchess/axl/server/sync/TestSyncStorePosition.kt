package proj.memorchess.axl.server.sync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.sync.DevicePlatform
import proj.memorchess.axl.core.sync.NodeSyncRow
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.server.db.PostgresTestDb

internal class TestSyncStorePosition {

  private val store = SyncStore(PostgresTestDb.dataSource())

  /** A fresh user with one registered device, which pushing now requires. */
  private suspend fun newUser(): String {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now)
    return user
  }

  private val now = Instant.fromEpochMilliseconds(1_000_000)

  private fun setting(key: String, value: String) =
    SettingSyncRow(
      key = key,
      value = value,
      isDeleted = false,
      updatedAt = now,
      originDevice = DEVICE,
      deviceSeq = 1,
    )

  private fun node(suffix: String) =
    NodeSyncRow(
      positionKey = "fen-${System.nanoTime()}-$suffix",
      dueDate = now,
      lastReview = null,
      firstReview = null,
      stability = 1.0,
      difficulty = 1.0,
      reps = 0,
      lapses = 0,
      phase = "NEW",
      step = 0,
      isDeleted = false,
      updatedAt = now,
      originDevice = DEVICE,
      deviceSeq = 1,
    )

  /** A fresh user with one registered device and [rows] already pushed. */
  private suspend fun registeredUserWith(vararg rows: SettingSyncRow): String {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now)
    if (rows.isNotEmpty()) {
      store.push(
        user,
        DEVICE,
        SyncPushRequest(emptyList(), emptyList(), rows.toList(), device = DEVICE),
        now,
      )
    }
    return user
  }

  @Test
  fun aFirstPullServesFromZeroAndAcknowledgesNothing() = runTest {
    val user = registeredUserWith(setting("a", "1"))

    val page = store.pull(user, DEVICE, ack = null, limit = 100, now)

    page.settings shouldHaveSize 1
    page.pageToken shouldNotBe ""
    val device = store.listDevicesForTest(user).single()
    device.lastAcked shouldBe 0L
    device.lastServed shouldBeGreaterThan 0L
  }

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
  fun anAckMatchingNothingConfirmsNothingAndIsNotAnError() = runTest {
    val user = registeredUserWith(setting("a", "1"))
    store.pull(user, DEVICE, ack = null, limit = 100, now)

    store.pull(user, DEVICE, ack = "not-a-token", limit = 100, now).settings shouldHaveSize 1

    store.listDevicesForTest(user).single().lastAcked shouldBe 0L
  }

  @Test
  fun aLostResponseIsReServedAndConfirmsNothing() = runTest {
    val user = registeredUserWith(setting("a", "1"), setting("b", "2"))
    val first = store.pull(user, DEVICE, ack = null, limit = 1, now)
    // The client never receives this one, so it never learns its token.
    store.pull(user, DEVICE, ack = first.pageToken, limit = 1, now)
    val before = store.listDevicesForTest(user).single().lastAcked

    val retry = store.pull(user, DEVICE, ack = null, limit = 1, now)

    retry.settings shouldHaveSize 1
    store.listDevicesForTest(user).single().lastAcked shouldBe before
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
  fun theAcknowledgementNeverRewinds() = runTest {
    val user = registeredUserWith(setting("a", "1"))
    val first = store.pull(user, DEVICE, ack = null, limit = 100, now)
    store.pull(user, DEVICE, ack = first.pageToken, limit = 100, now)
    val acked = store.listDevicesForTest(user).single().lastAcked

    repeat(3) { store.pull(user, DEVICE, ack = null, limit = 100, now) }

    store.listDevicesForTest(user).single().lastAcked shouldBe acked
  }

  @Test
  fun anUnknownDeviceIsRefused() = runTest {
    shouldThrow<UnknownDeviceException> {
      store.pull(newUser(), "never-registered", null, 100, now)
    }
  }

  @Test
  fun aRemovedDeviceBelowTheFloorIsRefused() = runTest {
    val user = registeredUserWith(setting("a", "1"))
    store.setPositionForTest(user, DEVICE, lastAcked = 99, lastServed = 99)
    store.removeDeviceForTest(user, DEVICE, now)
    store.setGcFloorForTest(user, 100)

    shouldThrow<ResyncRequiredException> { store.pull(user, DEVICE, ack = null, limit = 100, now) }
  }

  @Test
  fun aPushFromAnUnknownDeviceIsRefused() = runTest {
    shouldThrow<UnknownDeviceException> {
      store.push(
        PostgresTestDb.newUserId(),
        "never-registered",
        SyncPushRequest(emptyList(), emptyList(), listOf(setting("a", "1")), device = DEVICE),
        now,
      )
    }
  }

  @Test
  fun aPushFromARemovedDeviceBelowTheFloorIsRefused() = runTest {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now)
    store.setPositionForTest(user, DEVICE, lastAcked = 99, lastServed = 99)
    store.removeDeviceForTest(user, DEVICE, now)
    store.setGcFloorForTest(user, 100)

    shouldThrow<ResyncRequiredException> {
      store.push(
        user,
        DEVICE,
        SyncPushRequest(emptyList(), emptyList(), listOf(setting("a", "1")), device = DEVICE),
        now,
      )
    }
  }

  @Test
  fun aPushNeverMovesEitherPositionColumn() = runTest {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, now)

    store.push(
      user,
      DEVICE,
      SyncPushRequest(emptyList(), emptyList(), listOf(setting("a", "1")), device = DEVICE),
      now,
    )

    val device = store.listDevicesForTest(user).single()
    device.lastAcked shouldBe 0L
    device.lastServed shouldBe 0L
  }

  private companion object {
    const val DEVICE = "22222222-2222-4222-8222-222222222222"
  }
}
