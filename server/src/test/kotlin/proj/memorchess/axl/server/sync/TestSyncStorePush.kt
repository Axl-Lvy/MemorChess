package proj.memorchess.axl.server.sync

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.sync.EdgeSyncRow
import proj.memorchess.axl.core.sync.NodeSyncRow
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

  private fun node(
    key: String,
    reps: Int,
    at: Instant,
    device: String = "device-a",
    seq: Long = 1,
  ) =
    NodeSyncRow(
      positionKey = key,
      dueDate = at,
      lastReview = null,
      firstReview = null,
      stability = 1.5,
      difficulty = 5.0,
      reps = reps,
      lapses = 0,
      phase = "REVIEW",
      step = 0,
      isDeleted = false,
      updatedAt = at,
      originDevice = device,
      deviceSeq = seq,
    )

  private fun edge(
    origin: String,
    destination: String,
    isGood: Boolean,
    at: Instant,
    device: String = "device-a",
    seq: Long = 1,
  ) =
    EdgeSyncRow(
      origin = origin,
      destination = destination,
      move = "e4",
      isGood = isGood,
      isDeleted = false,
      updatedAt = at,
      originDevice = device,
      deviceSeq = seq,
    )

  private fun fen(suffix: String) = "fen-${System.nanoTime()}-$suffix"

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
  fun aNewerNodeReplacesTheOlderOne() = runTest {
    val user = PostgresTestDb.newUserId()
    val key = fen("node")
    store.push(user, SyncPushRequest(listOf(node(key, 1, serverNow, seq = 1)), emptyList(), emptyList()), serverNow)
    store.push(user, SyncPushRequest(listOf(node(key, 7, serverNow, seq = 2)), emptyList(), emptyList()), serverNow)
    store.readNodeForTest(user, key)?.reps shouldBe 7
  }

  @Test
  fun aNodeCarriesItsFullFsrsStateThroughTheRoundTrip() = runTest {
    val user = PostgresTestDb.newUserId()
    val key = fen("fsrs")
    val row = node(key, 3, serverNow).copy(lastReview = serverNow, firstReview = serverNow, lapses = 2)
    store.push(user, SyncPushRequest(listOf(row), emptyList(), emptyList()), serverNow)
    store.readNodeForTest(user, key) shouldBe row
  }

  @Test
  fun aNodeBeyondTheToleranceIsRefusedAndNotStored() = runTest {
    val user = PostgresTestDb.newUserId()
    val key = fen("late-node")
    val response =
      store.push(
        user,
        SyncPushRequest(
          listOf(node(key, 1, serverNow + SYNC_SKEW_TOLERANCE + 1.milliseconds)),
          emptyList(),
          emptyList(),
        ),
        serverNow,
      )
    response.rejected shouldHaveSize 1
    response.rejected.single().kind shouldBe "node"
    response.rejected.single().id shouldBe key
    store.readNodeForTest(user, key) shouldBe null
  }

  @Test
  fun aNewerEdgeReplacesTheOlderOne() = runTest {
    val user = PostgresTestDb.newUserId()
    val origin = fen("o")
    val destination = fen("d")
    val first = edge(origin, destination, isGood = true, at = serverNow, seq = 1)
    store.push(user, SyncPushRequest(emptyList(), listOf(first), emptyList()), serverNow)
    store.push(
      user,
      SyncPushRequest(emptyList(), listOf(first.copy(isGood = false, deviceSeq = 2)), emptyList()),
      serverNow,
    )
    store.readEdgeForTest(user, first)?.isGood shouldBe false
  }

  @Test
  fun anEdgeBeyondTheToleranceIsRefusedAndNotStored() = runTest {
    val user = PostgresTestDb.newUserId()
    val origin = fen("late-o")
    val destination = fen("late-d")
    val late =
      edge(origin, destination, isGood = true, at = serverNow + SYNC_SKEW_TOLERANCE + 1.milliseconds)
    val response = store.push(user, SyncPushRequest(emptyList(), listOf(late), emptyList()), serverNow)
    response.rejected shouldHaveSize 1
    response.rejected.single().kind shouldBe "edge"
    response.rejected.single().id shouldBe "$origin|$destination"
    store.readEdgeForTest(user, late) shouldBe null
  }

  @Test
  fun allThreeResourcesApplyInOnePush() = runTest {
    val user = PostgresTestDb.newUserId()
    val key = fen("mixed-node")
    val origin = fen("mixed-o")
    val destination = fen("mixed-d")
    val theEdge = edge(origin, destination, isGood = true, at = serverNow)
    store.push(
      user,
      SyncPushRequest(
        nodes = listOf(node(key, 1, serverNow)),
        edges = listOf(theEdge),
        settings = listOf(setting("theme", "dark", serverNow)),
      ),
      serverNow,
    )
    store.readNodeForTest(user, key)?.reps shouldBe 1
    store.readEdgeForTest(user, theEdge)?.isGood shouldBe true
    store.readSettingForTest(user, "theme")?.value shouldBe "dark"
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
