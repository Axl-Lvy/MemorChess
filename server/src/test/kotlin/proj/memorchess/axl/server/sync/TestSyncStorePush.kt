package proj.memorchess.axl.server.sync

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.sync.RejectionCode
import proj.memorchess.axl.core.sync.SYNC_SKEW_TOLERANCE
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.server.db.PostgresTestDb

internal class TestSyncStorePush {

  private val store = SyncStore(PostgresTestDb.dataSource())
  private val serverNow = Instant.fromEpochMilliseconds(1_000_000)

  private fun setting(
    key: String,
    value: String,
    at: Instant,
    device: String = "device-a",
    seq: Long = 1,
    deleted: Boolean = false,
  ) =
    SettingSyncRow(
      key = key,
      value = value,
      isDeleted = deleted,
      updatedAt = at,
      originDevice = device,
      deviceSeq = seq,
    )

  private fun request(vararg settings: SettingSyncRow) =
    SyncPushRequest(nodes = emptyList(), edges = emptyList(), settings = settings.toList())

  @Test
  fun anEmptyPushIsAccepted() = runTest {
    val response = store.push(PostgresTestDb.newUserId(), request(), serverNow)
    response.rejected.shouldBeEmpty()
    response.serverTime shouldBe serverNow
  }

  @Test
  fun aFirstWriteIsStoredAndGetsARevision() = runTest {
    val user = PostgresTestDb.newUserId()
    val response = store.push(user, request(setting("theme", "dark", serverNow)), serverNow)
    response.rejected.shouldBeEmpty()
    (response.revision > 0) shouldBe true
  }

  @Test
  fun aNewerWriteFromTheSameDeviceReplacesTheOlderOne() = runTest {
    val user = PostgresTestDb.newUserId()
    store.push(user, request(setting("theme", "dark", serverNow, seq = 1)), serverNow)
    store.push(user, request(setting("theme", "light", serverNow, seq = 2)), serverNow)
    store.readSettingForTest(user, "theme")?.value shouldBe "light"
  }

  @Test
  fun anOlderWriteFromTheSameDeviceLosesOnSequence() = runTest {
    val user = PostgresTestDb.newUserId()
    store.push(user, request(setting("theme", "dark", serverNow, seq = 5)), serverNow)
    store.push(user, request(setting("theme", "light", serverNow, seq = 2)), serverNow)
    store.readSettingForTest(user, "theme")?.value shouldBe "dark"
  }

  @Test
  fun aLaterWriteFromAnotherDeviceWinsOnTime() = runTest {
    val user = PostgresTestDb.newUserId()
    store.push(user, request(setting("theme", "dark", Instant.fromEpochMilliseconds(10))), serverNow)
    store.push(
      user,
      request(setting("theme", "light", Instant.fromEpochMilliseconds(20), device = "device-b")),
      serverNow,
    )
    store.readSettingForTest(user, "theme")?.value shouldBe "light"
  }

  @Test
  fun aLosingPushStillAdvancesTheSurvivingRowsRevision() = runTest {
    val user = PostgresTestDb.newUserId()
    val first =
      store.push(
        user,
        request(setting("theme", "dark", Instant.fromEpochMilliseconds(20))),
        serverNow,
      )
    val second =
      store.push(
        user,
        request(setting("theme", "light", Instant.fromEpochMilliseconds(10), device = "device-b")),
        serverNow,
      )
    // The pushed row lost, yet the revision must move, or the pusher never learns and diverges.
    (second.revision > first.revision) shouldBe true
    store.readSettingForTest(user, "theme")?.value shouldBe "dark"
  }

  @Test
  fun anIdenticalReplayDoesNotAdvanceTheRevision() = runTest {
    val user = PostgresTestDb.newUserId()
    val row = setting("theme", "dark", serverNow)
    store.push(user, request(row), serverNow)
    store.push(user, request(row), serverNow).revision shouldBe 0L
  }

  @Test
  fun aRowExactlyAtTheSkewToleranceIsAccepted() = runTest {
    val user = PostgresTestDb.newUserId()
    store
      .push(user, request(setting("theme", "dark", serverNow + SYNC_SKEW_TOLERANCE)), serverNow)
      .rejected
      .shouldBeEmpty()
  }

  @Test
  fun aRowOneMillisecondBeyondTheToleranceIsRefusedAndNotStored() = runTest {
    val user = PostgresTestDb.newUserId()
    val response =
      store.push(
        user,
        request(setting("theme", "dark", serverNow + SYNC_SKEW_TOLERANCE + 1.milliseconds)),
        serverNow,
      )
    response.rejected shouldHaveSize 1
    response.rejected.single().code shouldBe RejectionCode.CLOCK_TOO_FAR_AHEAD
    response.rejected.single().id shouldBe "theme"
    store.readSettingForTest(user, "theme") shouldBe null
  }

  @Test
  fun aRefusedRowDoesNotBlockTheRestOfTheBatch() = runTest {
    val user = PostgresTestDb.newUserId()
    val response =
      store.push(
        user,
        request(
          setting("bad", "x", serverNow + SYNC_SKEW_TOLERANCE + 1.milliseconds),
          setting("good", "y", serverNow),
        ),
        serverNow,
      )
    response.rejected shouldHaveSize 1
    store.readSettingForTest(user, "good")?.value shouldBe "y"
  }

  @Test
  fun anEpochZeroTimestampIsAnOrdinaryWrite() = runTest {
    val user = PostgresTestDb.newUserId()
    store
      .push(user, request(setting("theme", "dark", Instant.fromEpochMilliseconds(0))), serverNow)
      .rejected
      .shouldBeEmpty()
    store.readSettingForTest(user, "theme")?.value shouldBe "dark"
  }

  @Test
  fun aSubMillisecondTimestampSurvivesTheRoundTrip() = runTest {
    // Postgres timestamptz keeps microseconds. A millisecond conversion would move this value,
    // and a stored row must be byte identical to the row that was sent.
    val user = PostgresTestDb.newUserId()
    val precise = Instant.fromEpochSeconds(1_000, 123_456_000)
    store.push(user, request(setting("theme", "dark", precise)), serverNow)
    store.readSettingForTest(user, "theme")?.updatedAt shouldBe precise
  }

  @Test
  fun oneUsersRowsAreInvisibleToAnother() = runTest {
    val first = PostgresTestDb.newUserId()
    val second = PostgresTestDb.newUserId()
    store.push(first, request(setting("theme", "dark", serverNow)), serverNow)
    store.readSettingForTest(second, "theme") shouldBe null
  }

  @Test
  fun aTombstoneIsStoredLikeAnyOtherWrite() = runTest {
    val user = PostgresTestDb.newUserId()
    store.push(user, request(setting("theme", "dark", serverNow, seq = 1)), serverNow)
    store.push(
      user,
      request(setting("theme", "dark", serverNow, seq = 2, deleted = true)),
      serverNow,
    )
    store.readSettingForTest(user, "theme")?.isDeleted shouldBe true
  }
}
