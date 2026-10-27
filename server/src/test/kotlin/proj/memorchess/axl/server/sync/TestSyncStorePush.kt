package proj.memorchess.axl.server.sync

import proj.memorchess.axl.core.sync.Revision
import proj.memorchess.axl.server.auth.UserId
import proj.memorchess.axl.core.sync.DeviceId
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.sync.DevicePlatform
import proj.memorchess.axl.core.sync.EdgeRepertoireTagSyncRow
import proj.memorchess.axl.core.sync.EdgeSyncRow
import proj.memorchess.axl.core.sync.NodeSyncRow
import proj.memorchess.axl.core.sync.RejectionCode
import proj.memorchess.axl.core.sync.RepertoireSyncRow
import proj.memorchess.axl.core.sync.SYNC_SKEW_TOLERANCE
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.core.sync.resolve
import proj.memorchess.axl.server.db.PostgresTestDb

internal class TestSyncStorePush {

  private val store = SyncStore(PostgresTestDb.dataSource())

  /** A fresh user with one registered device, which pushing requires. */
  private suspend fun newUser(): UserId {
    val user = PostgresTestDb.newUserId()
    store.registerDevice(user, DEVICE, DevicePlatform.JVM, afterReset = false, serverNow)
    return user
  }

  private val serverNow = Instant.fromEpochMilliseconds(1_000_000)

  private fun setting(
    key: String,
    value: String,
    at: Instant,
    device: DeviceId = DeviceId("device-a"),
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
    SyncPushRequest(
      nodes = emptyList(),
      edges = emptyList(),
      settings = settings.toList(),
      device = DEVICE,
    )

  private fun node(
    key: String,
    reps: Int,
    at: Instant,
    device: DeviceId = DeviceId("device-a"),
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
    device: DeviceId = DeviceId("device-a"),
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

  /** Everything [user] has stored, as [DEVICE] would be served it from scratch. */
  private suspend fun stored(user: UserId) = store.pull(user, DEVICE, null, PULL_LIMIT, serverNow)

  private suspend fun storedSetting(user: UserId, key: String) =
    stored(user).settings.singleOrNull { it.key == key }

  private suspend fun storedNode(user: UserId, key: String) =
    stored(user).nodes.singleOrNull { it.positionKey == key }

  private suspend fun storedEdge(user: UserId, edge: EdgeSyncRow) =
    stored(user).edges.singleOrNull {
      it.origin == edge.origin && it.destination == edge.destination
    }

  @Test
  fun anEmptyPushIsAccepted() = runTest {
    val response = store.push(newUser(), DEVICE, request(), serverNow)
    response.rejected.shouldBeEmpty()
    response.serverTime shouldBe serverNow
  }

  @Test
  fun aFirstWriteIsStoredAndGetsARevision() = runTest {
    val user = newUser()
    val response = store.push(user, DEVICE, request(setting("theme", "dark", serverNow)), serverNow)
    response.rejected.shouldBeEmpty()
    (response.revision > Revision.ZERO) shouldBe true
  }

  @Test
  fun aNewerWriteFromTheSameDeviceReplacesTheOlderOne() = runTest {
    val user = newUser()
    store.push(user, DEVICE, request(setting("theme", "dark", serverNow, seq = 1)), serverNow)
    store.push(user, DEVICE, request(setting("theme", "light", serverNow, seq = 2)), serverNow)
    storedSetting(user, "theme")?.value shouldBe "light"
  }

  @Test
  fun anOlderWriteFromTheSameDeviceLosesOnSequence() = runTest {
    val user = newUser()
    store.push(user, DEVICE, request(setting("theme", "dark", serverNow, seq = 5)), serverNow)
    store.push(user, DEVICE, request(setting("theme", "light", serverNow, seq = 2)), serverNow)
    storedSetting(user, "theme")?.value shouldBe "dark"
  }

  @Test
  fun aLaterWriteFromAnotherDeviceWinsOnTime() = runTest {
    val user = newUser()
    store.push(
      user,
      DEVICE,
      request(setting("theme", "dark", Instant.fromEpochMilliseconds(10))),
      serverNow,
    )
    store.push(
      user,
      DEVICE,
      request(setting("theme", "light", Instant.fromEpochMilliseconds(20), device = DeviceId("device-b"))),
      serverNow,
    )
    storedSetting(user, "theme")?.value shouldBe "light"
  }

  @Test
  fun concurrentPushesOfTwoVersionsOfTheSameSettingConvergeToTheResolvedWinner() = runTest {
    // Without the row lock in applySetting, both pushes could read the same stale row and the last
    // commit would keep a winner `resolve` never allowed.
    val user = newUser()
    val fromDeviceA =
      setting("theme", "dark", Instant.fromEpochMilliseconds(10), device = DeviceId("device-a"))
    val fromDeviceB =
      setting("theme", "light", Instant.fromEpochMilliseconds(20), device = DeviceId("device-b"))
    val expectedWinner = resolve(local = fromDeviceA, remote = fromDeviceB).row

    coroutineScope {
      val first =
        async(Dispatchers.IO) { store.push(user, DEVICE, request(fromDeviceA), serverNow) }
      val second =
        async(Dispatchers.IO) { store.push(user, DEVICE, request(fromDeviceB), serverNow) }
      first.await()
      second.await()
    }

    storedSetting(user, "theme") shouldBe expectedWinner
  }

  @Test
  fun aLosingPushStillAdvancesTheSurvivingRowsRevision() = runTest {
    val user = newUser()
    val first =
      store.push(
        user,
        DEVICE,
        request(setting("theme", "dark", Instant.fromEpochMilliseconds(20))),
        serverNow,
      )
    val second =
      store.push(
        user,
        DEVICE,
        request(setting("theme", "light", Instant.fromEpochMilliseconds(10), device = DeviceId("device-b"))),
        serverNow,
      )
    // The pushed row lost, yet the revision must move, or the pusher never learns and diverges.
    (second.revision > first.revision) shouldBe true
    storedSetting(user, "theme")?.value shouldBe "dark"
  }

  @Test
  fun anIdenticalReplayDoesNotAdvanceTheRevision() = runTest {
    val user = newUser()
    val row = setting("theme", "dark", serverNow)
    store.push(user, DEVICE, request(row), serverNow)
    store.push(user, DEVICE, request(row), serverNow).revision shouldBe Revision(0)
  }

  @Test
  fun aRowExactlyAtTheSkewToleranceIsAccepted() = runTest {
    val user = newUser()
    store
      .push(
        user,
        DEVICE,
        request(setting("theme", "dark", serverNow + SYNC_SKEW_TOLERANCE)),
        serverNow,
      )
      .rejected
      .shouldBeEmpty()
  }

  @Test
  fun aRowOneMillisecondBeyondTheToleranceIsRefusedAndNotStored() = runTest {
    val user = newUser()
    val response =
      store.push(
        user,
        DEVICE,
        request(setting("theme", "dark", serverNow + SYNC_SKEW_TOLERANCE + 1.milliseconds)),
        serverNow,
      )
    response.rejected shouldHaveSize 1
    response.rejected.single().code shouldBe RejectionCode.CLOCK_TOO_FAR_AHEAD
    response.rejected.single().id shouldBe "theme"
    storedSetting(user, "theme") shouldBe null
  }

  @Test
  fun aRefusedRowDoesNotBlockTheRestOfTheBatch() = runTest {
    val user = newUser()
    val response =
      store.push(
        user,
        DEVICE,
        request(
          setting("bad", "x", serverNow + SYNC_SKEW_TOLERANCE + 1.milliseconds),
          setting("good", "y", serverNow),
        ),
        serverNow,
      )
    response.rejected shouldHaveSize 1
    storedSetting(user, "good")?.value shouldBe "y"
  }

  @Test
  fun anEpochZeroTimestampIsAnOrdinaryWrite() = runTest {
    val user = newUser()
    store
      .push(
        user,
        DEVICE,
        request(setting("theme", "dark", Instant.fromEpochMilliseconds(0))),
        serverNow,
      )
      .rejected
      .shouldBeEmpty()
    storedSetting(user, "theme")?.value shouldBe "dark"
  }

  @Test
  fun aSubMillisecondTimestampSurvivesTheRoundTrip() = runTest {
    // timestamptz keeps microseconds; a millisecond conversion would alter the stored row.
    val user = newUser()
    val precise = Instant.fromEpochSeconds(1_000, 123_456_000)
    store.push(user, DEVICE, request(setting("theme", "dark", precise)), serverNow)
    storedSetting(user, "theme")?.updatedAt shouldBe precise
  }

  @Test
  fun oneUsersRowsAreInvisibleToAnother() = runTest {
    val first = newUser()
    val second = newUser()
    store.push(first, DEVICE, request(setting("theme", "dark", serverNow)), serverNow)
    storedSetting(second, "theme") shouldBe null
  }

  @Test
  fun aNewerNodeReplacesTheOlderOne() = runTest {
    val user = newUser()
    val key = fen("node")
    store.push(
      user,
      DEVICE,
      SyncPushRequest(
        listOf(node(key, 1, serverNow, seq = 1)),
        emptyList(),
        emptyList(),
        device = DEVICE,
      ),
      serverNow,
    )
    store.push(
      user,
      DEVICE,
      SyncPushRequest(
        listOf(node(key, 7, serverNow, seq = 2)),
        emptyList(),
        emptyList(),
        device = DEVICE,
      ),
      serverNow,
    )
    storedNode(user, key)?.reps shouldBe 7
  }

  @Test
  fun aNodeCarriesItsFullFsrsStateThroughTheRoundTrip() = runTest {
    val user = newUser()
    val key = fen("fsrs")
    val row =
      node(key, 3, serverNow).copy(lastReview = serverNow, firstReview = serverNow, lapses = 2)
    store.push(
      user,
      DEVICE,
      SyncPushRequest(listOf(row), emptyList(), emptyList(), device = DEVICE),
      serverNow,
    )
    storedNode(user, key) shouldBe row
  }

  @Test
  fun aNodeBeyondTheToleranceIsRefusedAndNotStored() = runTest {
    val user = newUser()
    val key = fen("late-node")
    val response =
      store.push(
        user,
        DEVICE,
        SyncPushRequest(
          listOf(node(key, 1, serverNow + SYNC_SKEW_TOLERANCE + 1.milliseconds)),
          emptyList(),
          emptyList(),
          device = DEVICE,
        ),
        serverNow,
      )
    response.rejected shouldHaveSize 1
    response.rejected.single().kind shouldBe "node"
    response.rejected.single().id shouldBe key
    storedNode(user, key) shouldBe null
  }

  @Test
  fun aNewerEdgeReplacesTheOlderOne() = runTest {
    val user = newUser()
    val origin = fen("o")
    val destination = fen("d")
    val first = edge(origin, destination, isGood = true, at = serverNow, seq = 1)
    store.push(
      user,
      DEVICE,
      SyncPushRequest(emptyList(), listOf(first), emptyList(), device = DEVICE),
      serverNow,
    )
    store.push(
      user,
      DEVICE,
      SyncPushRequest(
        emptyList(),
        listOf(first.copy(isGood = false, deviceSeq = 2)),
        emptyList(),
        device = DEVICE,
      ),
      serverNow,
    )
    storedEdge(user, first)?.isGood shouldBe false
  }

  @Test
  fun anEdgeBeyondTheToleranceIsRefusedAndNotStored() = runTest {
    val user = newUser()
    val origin = fen("late-o")
    val destination = fen("late-d")
    val late =
      edge(
        origin,
        destination,
        isGood = true,
        at = serverNow + SYNC_SKEW_TOLERANCE + 1.milliseconds,
      )
    val response =
      store.push(
        user,
        DEVICE,
        SyncPushRequest(emptyList(), listOf(late), emptyList(), device = DEVICE),
        serverNow,
      )
    response.rejected shouldHaveSize 1
    response.rejected.single().kind shouldBe "edge"
    response.rejected.single().id shouldBe "$origin|$destination"
    storedEdge(user, late) shouldBe null
  }

  @Test
  fun allThreeResourcesApplyInOnePush() = runTest {
    val user = newUser()
    val key = fen("mixed-node")
    val origin = fen("mixed-o")
    val destination = fen("mixed-d")
    val theEdge = edge(origin, destination, isGood = true, at = serverNow)
    store.push(
      user,
      DEVICE,
      SyncPushRequest(
        nodes = listOf(node(key, 1, serverNow)),
        edges = listOf(theEdge),
        settings = listOf(setting("theme", "dark", serverNow)),
        device = DEVICE,
      ),
      serverNow,
    )
    storedNode(user, key)?.reps shouldBe 1
    storedEdge(user, theEdge)?.isGood shouldBe true
    storedSetting(user, "theme")?.value shouldBe "dark"
  }

  @Test
  fun pushingARepertoireRoundTripsThroughPull() = runTest {
    val user = newUser()
    val row =
      RepertoireSyncRow(
        id = "italian-game",
        name = "Italian Game",
        color = "WHITE",
        isDeleted = false,
        updatedAt = serverNow,
        originDevice = DeviceId("device-a"),
        deviceSeq = 1L,
      )

    store.push(
      user,
      DEVICE,
      SyncPushRequest(
        nodes = emptyList(),
        edges = emptyList(),
        settings = emptyList(),
        repertoires = listOf(row),
        tags = emptyList(),
        device = DEVICE,
      ),
      serverNow,
    )
    val repertoires = stored(user).repertoires

    repertoires shouldBe listOf(row)
  }

  @Test
  fun pushingAnEdgeRepertoireTagRoundTripsThroughPull() = runTest {
    val user = newUser()
    val origin = fen("tag-o")
    val destination = fen("tag-d")
    val theEdge = edge(origin, destination, isGood = true, at = serverNow)
    val tag =
      EdgeRepertoireTagSyncRow(
        origin = origin,
        destination = destination,
        repertoireId = "italian-game",
        move = "e4",
        isDeleted = false,
        updatedAt = serverNow,
        originDevice = DeviceId("device-a"),
        deviceSeq = 1L,
      )

    store.push(
      user,
      DEVICE,
      SyncPushRequest(
        nodes = emptyList(),
        edges = listOf(theEdge),
        settings = emptyList(),
        repertoires = emptyList(),
        tags = listOf(tag),
        device = DEVICE,
      ),
      serverNow,
    )
    val tags = stored(user).tags

    tags shouldBe listOf(tag)
  }

  @Test
  fun aTagNamingAnEdgeTheServerHasNeverSeenInternsIt() = runTest {
    val user = newUser()
    val origin = fen("orphan-o")
    val destination = fen("orphan-d")
    val tag =
      EdgeRepertoireTagSyncRow(
        origin = origin,
        destination = destination,
        repertoireId = "italian-game",
        move = "Nf3",
        isGood = true,
        isDeleted = false,
        updatedAt = serverNow,
        originDevice = DeviceId("device-a"),
        deviceSeq = 1L,
      )

    val response =
      store.push(
        user,
        DEVICE,
        SyncPushRequest(
          nodes = emptyList(),
          edges = emptyList(),
          settings = emptyList(),
          repertoires = emptyList(),
          tags = listOf(tag),
          device = DEVICE,
        ),
        serverNow,
      )

    response.rejected.shouldBeEmpty()
    stored(user).tags shouldBe listOf(tag)
  }

  @Test
  fun aTagMarkingAndARepertoireInstalledVersionSurvivePushAndPull() = runTest {
    val user = newUser()
    val repertoire =
      RepertoireSyncRow(
        id = "london",
        name = "London",
        color = "WHITE",
        installedVersion = 2,
        isDeleted = false,
        updatedAt = serverNow,
        originDevice = DeviceId("device-a"),
        deviceSeq = 1L,
      )
    val tag =
      EdgeRepertoireTagSyncRow(
        origin = fen("mark-o"),
        destination = fen("mark-d"),
        repertoireId = "london",
        move = "d4",
        isGood = true,
        isDeleted = false,
        updatedAt = serverNow,
        originDevice = DeviceId("device-a"),
        deviceSeq = 2L,
      )

    store.push(user, DEVICE, tagsRequest(listOf(repertoire), listOf(tag)), serverNow)
    val pulled = stored(user)

    pulled.repertoires shouldBe listOf(repertoire)
    pulled.tags shouldBe listOf(tag)
  }

  @Test
  fun aLaterTagPushReplacesTheStoredMarking() = runTest {
    val user = newUser()
    val good =
      EdgeRepertoireTagSyncRow(
        origin = fen("remark-o"),
        destination = fen("remark-d"),
        repertoireId = "london",
        move = "d4",
        isGood = true,
        isDeleted = false,
        updatedAt = serverNow,
        originDevice = DeviceId("device-a"),
        deviceSeq = 1L,
      )
    val bad = good.copy(isGood = false, deviceSeq = 2L)

    store.push(user, DEVICE, tagsRequest(emptyList(), listOf(good)), serverNow)
    store.push(user, DEVICE, tagsRequest(emptyList(), listOf(bad)), serverNow)

    stored(user).tags.single().isGood shouldBe false
  }

  private fun tagsRequest(
    repertoires: List<RepertoireSyncRow>,
    tags: List<EdgeRepertoireTagSyncRow>,
  ) =
    SyncPushRequest(
      nodes = emptyList(),
      edges = emptyList(),
      settings = emptyList(),
      repertoires = repertoires,
      tags = tags,
      device = DEVICE,
    )

  @Test
  fun aTombstoneIsStoredLikeAnyOtherWrite() = runTest {
    val user = newUser()
    store.push(user, DEVICE, request(setting("theme", "dark", serverNow, seq = 1)), serverNow)
    store.push(
      user,
      DEVICE,
      request(setting("theme", "dark", serverNow, seq = 2, deleted = true)),
      serverNow,
    )
    storedSetting(user, "theme")?.isDeleted shouldBe true
  }

  private companion object {
    val DEVICE = DeviceId("77777777-7777-4777-8777-777777777777")
    const val PULL_LIMIT = 500
  }
}
