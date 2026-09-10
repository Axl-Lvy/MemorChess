package proj.memorchess.axl.server.sync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.sync.DevicePlatform
import proj.memorchess.axl.core.sync.NodeSyncRow
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.server.db.PostgresTestDb

internal class TestSyncStoreGc {

  private val store = SyncStore(PostgresTestDb.dataSource())
  private val now = Instant.fromEpochMilliseconds(1_000_000)

  private fun setting(key: String, value: String, seq: Long = 1, deleted: Boolean = false) =
    SettingSyncRow(
      key = key,
      value = value,
      isDeleted = deleted,
      updatedAt = now,
      originDevice = DEVICE_A,
      deviceSeq = seq,
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
      originDevice = DEVICE_A,
      deviceSeq = 1,
    )

  private suspend fun register(user: String, vararg devices: String) {
    for (device in devices) {
      store.registerDevice(user, device, DevicePlatform.JVM, afterReset = false, now)
    }
  }

  private suspend fun push(user: String, device: String, vararg rows: SettingSyncRow) =
    store.push(user, device, SyncPushRequest(emptyList(), emptyList(), rows.toList()), now)

  /** Pulls and confirms until [device] has committed everything, the way a real cycle does. */
  private suspend fun catchUp(user: String, device: String) {
    var ack: String? = null
    repeat(10) {
      val page = store.pull(user, device, ack, 500, now)
      if (page.settings.isEmpty() && page.nodes.isEmpty() && ack != null) return
      ack = page.pageToken
    }
  }

  private suspend fun settingsOf(user: String, device: String) =
    store.pull(user, device, null, 500, now).settings

  @Test
  fun aUserWithNoRegisteredDeviceIsNeverScanned() = runTest {
    val user = PostgresTestDb.newUserId()
    register(user, DEVICE_A)
    push(user, DEVICE_A, setting("a", "1"))
    catchUp(user, DEVICE_A)
    push(user, DEVICE_A, setting("a", "1", seq = 2, deleted = true))
    catchUp(user, DEVICE_A)
    store.removeDeviceForTest(user, DEVICE_A, now)

    store.collectTombstones()

    store.gcFloorForTest(user) shouldBe null
  }

  @Test
  fun aDeviceStillAtZeroBlocksTheWholeUser() = runTest {
    val user = PostgresTestDb.newUserId()
    register(user, DEVICE_A, DEVICE_B)
    push(user, DEVICE_A, setting("a", "1"))
    push(user, DEVICE_A, setting("a", "1", seq = 2, deleted = true))
    catchUp(user, DEVICE_A)

    store.collectTombstones()

    store.gcFloorForTest(user) shouldBe null
    settingsOf(user, DEVICE_B) shouldHaveSize 1
  }

  @Test
  fun everyDeviceCaughtUpPurgesTheTombstone() = runTest {
    val user = PostgresTestDb.newUserId()
    register(user, DEVICE_A, DEVICE_B)
    push(user, DEVICE_A, setting("a", "1"))
    push(user, DEVICE_A, setting("a", "1", seq = 2, deleted = true))
    catchUp(user, DEVICE_A)
    catchUp(user, DEVICE_B)

    store.collectTombstones()

    store.readSettingForTest(user, "a") shouldBe null
  }

  @Test
  fun aRemovedDeviceIsExcludedFromTheMinimum() = runTest {
    val user = PostgresTestDb.newUserId()
    register(user, DEVICE_A, DEVICE_B)
    push(user, DEVICE_A, setting("a", "1"))
    push(user, DEVICE_A, setting("a", "1", seq = 2, deleted = true))
    catchUp(user, DEVICE_A)
    store.removeDeviceForTest(user, DEVICE_B, now)

    store.collectTombstones()

    store.readSettingForTest(user, "a") shouldBe null
  }

  @Test
  fun aLiveRowBelowTheWatermarkIsUntouched() = runTest {
    val user = PostgresTestDb.newUserId()
    register(user, DEVICE_A)
    push(user, DEVICE_A, setting("keep", "1"))
    push(user, DEVICE_A, setting("gone", "1"))
    push(user, DEVICE_A, setting("gone", "1", seq = 2, deleted = true))
    catchUp(user, DEVICE_A)

    store.collectTombstones()

    store.readSettingForTest(user, "keep")?.value shouldBe "1"
    store.readSettingForTest(user, "gone") shouldBe null
  }

  @Test
  fun aTombstoneAboveTheWatermarkIsKept() = runTest {
    val user = PostgresTestDb.newUserId()
    register(user, DEVICE_A, DEVICE_B)
    push(user, DEVICE_A, setting("a", "1"))
    catchUp(user, DEVICE_A)
    catchUp(user, DEVICE_B)
    // Written after every device caught up, so it sits strictly above the watermark.
    push(user, DEVICE_A, setting("a", "1", seq = 2, deleted = true))

    store.collectTombstones()

    store.readSettingForTest(user, "a")?.isDeleted shouldBe true
  }

  @Test
  fun oneUsersWatermarkNeverPurgesAnothersRow() = runTest {
    val mine = PostgresTestDb.newUserId()
    val theirs = PostgresTestDb.newUserId()
    register(mine, DEVICE_A)
    register(theirs, DEVICE_A)
    push(theirs, DEVICE_A, setting("a", "1"))
    push(theirs, DEVICE_A, setting("a", "1", seq = 2, deleted = true))
    push(mine, DEVICE_A, setting("b", "1"))
    catchUp(mine, DEVICE_A)

    store.collectTombstones()

    store.readSettingForTest(theirs, "a")?.isDeleted shouldBe true
  }

  @Test
  fun collectingRecordsTheWatermarkAsTheFloor() = runTest {
    val user = PostgresTestDb.newUserId()
    register(user, DEVICE_A)
    push(user, DEVICE_A, setting("a", "1"))
    catchUp(user, DEVICE_A)
    val watermark = store.listDevicesForTest(user).single().lastAcked

    store.collectTombstones()

    store.gcFloorForTest(user) shouldBe watermark
  }

  @Test
  fun aSecondTickWithAnUnchangedWatermarkRewritesTheSameFloor() = runTest {
    val user = PostgresTestDb.newUserId()
    register(user, DEVICE_A)
    push(user, DEVICE_A, setting("a", "1"))
    catchUp(user, DEVICE_A)
    store.collectTombstones()
    val first = store.gcFloorForTest(user)

    store.collectTombstones()

    store.gcFloorForTest(user) shouldBe first
  }

  @Test
  fun collectingFreesRoomForAPushTheQuotaHadRefused() = runTest {
    val capped = SyncStore(PostgresTestDb.dataSource(), maxNodesPerUser = 1)
    val user = PostgresTestDb.newUserId()
    capped.registerDevice(user, DEVICE_A, DevicePlatform.JVM, afterReset = false, now)
    val doomed = node("doomed")
    capped.push(
      user,
      DEVICE_A,
      SyncPushRequest(listOf(doomed), emptyList(), emptyList()),
      now,
    )
    capped.push(
      user,
      DEVICE_A,
      SyncPushRequest(listOf(doomed.copy(isDeleted = true, deviceSeq = 2)), emptyList(), emptyList()),
      now,
    )
    var ack: String? = null
    repeat(5) {
      val page = capped.pull(user, DEVICE_A, ack, 500, now)
      if (page.nodes.isEmpty() && ack != null) return@repeat
      ack = page.pageToken
    }
    capped.pull(user, DEVICE_A, ack, 500, now)
    shouldThrow<QuotaExceededException> {
      capped.push(user, DEVICE_A, SyncPushRequest(listOf(node("wanted")), emptyList(), emptyList()), now)
    }

    capped.collectTombstones()

    capped.push(
      user,
      DEVICE_A,
      SyncPushRequest(listOf(node("wanted")), emptyList(), emptyList()),
      now,
    )
  }

  @Test
  fun deletingAUserClearsItsDevicesAndItsFloor() = runTest {
    val user = PostgresTestDb.newUserId()
    register(user, DEVICE_A)
    push(user, DEVICE_A, setting("a", "1"))
    catchUp(user, DEVICE_A)
    store.collectTombstones()

    store.deleteUser(user)

    store.listDevicesForTest(user).shouldBeEmpty()
    store.gcFloorForTest(user) shouldBe null
  }

  private companion object {
    const val DEVICE_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    const val DEVICE_B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
  }
}
