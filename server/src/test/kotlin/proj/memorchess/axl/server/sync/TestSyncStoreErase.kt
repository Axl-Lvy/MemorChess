package proj.memorchess.axl.server.sync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.sync.DevicePlatform
import proj.memorchess.axl.core.sync.EdgeRepertoireTagSyncRow
import proj.memorchess.axl.core.sync.EdgeSyncRow
import proj.memorchess.axl.core.sync.NodeSyncRow
import proj.memorchess.axl.core.sync.RepertoireSyncRow
import proj.memorchess.axl.core.sync.ResolutionSource
import proj.memorchess.axl.core.sync.SettingSyncRow
import proj.memorchess.axl.core.sync.SyncPushRequest
import proj.memorchess.axl.core.sync.SyncPushResponse
import proj.memorchess.axl.core.sync.SyncRow
import proj.memorchess.axl.core.sync.resolve
import proj.memorchess.axl.server.db.PostgresTestDb

internal class TestSyncStoreErase {

  private val store = SyncStore(PostgresTestDb.dataSource())
  private val now = Instant.fromEpochMilliseconds(1_000_000)

  /** Everything one device was served while catching up. */
  private class Caught(
    val nodes: List<NodeSyncRow>,
    val edges: List<EdgeSyncRow>,
    val settings: List<SettingSyncRow>,
    val repertoires: List<RepertoireSyncRow>,
    val tags: List<EdgeRepertoireTagSyncRow>,
  ) {
    /** Every served row except settings, which erase never touches. */
    val erasable: List<SyncRow>
      get() = nodes + edges + repertoires + tags
  }

  /** Pulls and confirms page after page until [device] has been served everything. */
  private suspend fun catchUp(user: String, device: String): Caught {
    val nodes = mutableListOf<NodeSyncRow>()
    val edges = mutableListOf<EdgeSyncRow>()
    val settings = mutableListOf<SettingSyncRow>()
    val repertoires = mutableListOf<RepertoireSyncRow>()
    val tags = mutableListOf<EdgeRepertoireTagSyncRow>()
    var ack: String? = null
    repeat(20) {
      val page = store.pull(user, device, ack, 500, now)
      nodes += page.nodes
      edges += page.edges
      settings += page.settings
      repertoires += page.repertoires
      tags += page.tags
      if (page.nextCursor == null) {
        // Confirm the last page too, so the next catch up starts after it.
        store.pull(user, device, page.pageToken, 500, now)
        return Caught(nodes, edges, settings, repertoires, tags)
      }
      ack = page.pageToken
    }
    error("catch up did not terminate")
  }

  private suspend fun newUser(): String {
    val user = PostgresTestDb.newUserId()
    for (device in listOf(ERASER, OTHER)) {
      store.registerDevice(user, device, DevicePlatform.JVM, afterReset = false, now)
    }
    return user
  }

  private fun node(key: String, updatedAt: Instant = now) =
    NodeSyncRow(
      positionKey = key,
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
      updatedAt = updatedAt,
      originDevice = OTHER,
      deviceSeq = 1,
    )

  private fun edge(origin: String, destination: String, updatedAt: Instant = now) =
    EdgeSyncRow(
      origin = origin,
      destination = destination,
      move = "e4",
      isGood = true,
      isDeleted = false,
      updatedAt = updatedAt,
      originDevice = OTHER,
      deviceSeq = 1,
    )

  private fun setting(updatedAt: Instant = now) =
    SettingSyncRow(
      key = "theme",
      value = "dark",
      isDeleted = false,
      updatedAt = updatedAt,
      originDevice = OTHER,
      deviceSeq = 1,
    )

  private fun repertoire(updatedAt: Instant = now) =
    RepertoireSyncRow(
      id = "italian-game",
      name = "Italian Game",
      color = "WHITE",
      isDeleted = false,
      updatedAt = updatedAt,
      originDevice = OTHER,
      deviceSeq = 1,
    )

  private fun tag(origin: String, destination: String, updatedAt: Instant = now) =
    EdgeRepertoireTagSyncRow(
      origin = origin,
      destination = destination,
      repertoireId = "italian-game",
      isDeleted = false,
      updatedAt = updatedAt,
      originDevice = OTHER,
      deviceSeq = 1,
    )

  private fun fen(suffix: String) = "fen-${System.nanoTime()}-$suffix"

  /** Pushes 2 nodes, 2 edges, 1 repertoire, 1 tag and 1 setting from [OTHER]. */
  private suspend fun populate(user: String): SyncPushRequest {
    val a = fen("a")
    val b = fen("b")
    val c = fen("c")
    val request =
      SyncPushRequest(
        nodes = listOf(node(a), node(b)),
        edges = listOf(edge(a, b), edge(b, c)),
        settings = listOf(setting()),
        repertoires = listOf(repertoire()),
        tags = listOf(tag(a, b)),
        device = OTHER,
      )
    store.push(user, OTHER, request, now)
    return request
  }

  /** Pushes one node, edge, repertoire and tag from [OTHER], all stamped [updatedAt]. */
  private suspend fun pushStamped(
    user: String,
    updatedAt: Instant,
    serverNow: Instant = now,
  ): Pair<SyncPushRequest, SyncPushResponse> {
    val a = fen("a")
    val b = fen("b")
    val request =
      SyncPushRequest(
        nodes = listOf(node(a, updatedAt)),
        edges = listOf(edge(a, b, updatedAt)),
        settings = emptyList(),
        repertoires = listOf(repertoire(updatedAt)),
        tags = listOf(tag(a, b, updatedAt)),
        device = OTHER,
      )
    return request to store.push(user, OTHER, request, serverNow)
  }

  /** Asserts the pusher of [pushed] resolves every served row in favour of the server's. */
  private fun Caught.pusherConvergesOn(pushed: SyncPushRequest) {
    resolve(pushed.nodes.single(), nodes.single()).source shouldBe ResolutionSource.REMOTE
    resolve(pushed.edges.single(), edges.single()).source shouldBe ResolutionSource.REMOTE
    resolve(pushed.repertoires.single(), repertoires.single()).source shouldBe
      ResolutionSource.REMOTE
    resolve(pushed.tags.single(), tags.single()).source shouldBe ResolutionSource.REMOTE
  }

  private fun Caught.shouldAllBe(deleted: Boolean) {
    erasable shouldHaveSize 4
    erasable.forEach { it.isDeleted shouldBe deleted }
  }

  @Test
  fun aNeverSeenRowStampedBeforeTheCutoffIsStoredAsATombstone() = runTest {
    val user = newUser()
    store.erase(user, ERASER, 42L, now)

    val (pushed, response) = pushStamped(user, now - 1.minutes)

    response.rejected.shouldBeEmpty()
    val caught = catchUp(user, OTHER)
    caught.shouldAllBe(true)
    caught.erasable.forEach {
      it.updatedAt shouldBe now
      it.originDevice shouldBe ERASE_ORIGIN
      it.deviceSeq shouldBe 0L
    }
    caught.pusherConvergesOn(pushed)
  }

  @Test
  fun aRowStampedAfterTheCutoffStaysLive() = runTest {
    val user = newUser()
    store.erase(user, ERASER, 42L, now)

    pushStamped(user, now + 1.seconds)

    val caught = catchUp(user, OTHER)
    caught.shouldAllBe(false)
    caught.erasable.forEach { it.updatedAt shouldBe now + 1.seconds }
  }

  @Test
  fun aRowStampedExactlyAtTheCutoffStaysLive() = runTest {
    val user = newUser()
    store.erase(user, ERASER, 42L, now)

    pushStamped(user, now)

    catchUp(user, OTHER).shouldAllBe(false)
  }

  @Test
  fun aRowStampedJustBeforeTheCutoffIsStoredAsATombstone() = runTest {
    val user = newUser()
    store.erase(user, ERASER, 42L, now)

    val (pushed, _) = pushStamped(user, now - 1.milliseconds)

    val caught = catchUp(user, OTHER)
    caught.shouldAllBe(true)
    caught.pusherConvergesOn(pushed)
  }

  @Test
  fun aUserWhoNeverErasedIsUnaffected() = runTest {
    val user = newUser()

    pushStamped(user, now - 1000.hours)

    catchUp(user, OTHER).shouldAllBe(false)
  }

  @Test
  fun aSecondEraseMovesTheCutoff() = runTest {
    val user = newUser()
    val second = now + 10.minutes
    store.erase(user, ERASER, 42L, now)
    store.erase(user, ERASER, 43L, second)

    val (pushed, _) = pushStamped(user, now + 5.minutes, serverNow = second)

    val caught = catchUp(user, OTHER)
    caught.shouldAllBe(true)
    caught.erasable.forEach { it.updatedAt shouldBe second }
    caught.pusherConvergesOn(pushed)
  }

  @Test
  fun anOfflineEditOfAKnownRowStampedBeforeTheCutoffConvergesOnTheTombstone() = runTest {
    val user = newUser()
    val first = node(fen("known"), now - 10.minutes)
    store.push(
      user,
      OTHER,
      SyncPushRequest(
        nodes = listOf(first),
        edges = emptyList(),
        settings = emptyList(),
        device = OTHER,
      ),
      now,
    )
    store.erase(user, ERASER, 42L, now)
    val edited = first.copy(reps = 3, updatedAt = now - 5.minutes, deviceSeq = 2)

    store.push(
      user,
      OTHER,
      SyncPushRequest(
        nodes = listOf(edited),
        edges = emptyList(),
        settings = emptyList(),
        device = OTHER,
      ),
      now,
    )

    val served = catchUp(user, OTHER).nodes.single()
    served.isDeleted shouldBe true
    resolve(edited, served).source shouldBe ResolutionSource.REMOTE
  }

  @Test
  fun settingsIgnoreTheCutoff() = runTest {
    val user = newUser()
    store.erase(user, ERASER, 42L, now)

    store.push(
      user,
      OTHER,
      SyncPushRequest(
        nodes = emptyList(),
        edges = emptyList(),
        settings = listOf(setting(now - 1.minutes)),
        device = OTHER,
      ),
      now,
    )

    val stored = catchUp(user, OTHER).settings.single()
    stored.isDeleted shouldBe false
    stored.updatedAt shouldBe now - 1.minutes
  }

  @Test
  fun deletingAUserAlsoForgetsItsEraseCutoff() = runTest {
    val user = newUser()
    store.erase(user, ERASER, 42L, now)
    store.deleteUser(user)
    store.registerDevice(user, OTHER, DevicePlatform.JVM, afterReset = false, now)

    pushStamped(user, now - 1.minutes)

    catchUp(user, OTHER).shouldAllBe(false)
  }

  @Test
  fun eraseTombstonesEveryLiveRowOfTheFourTables() = runTest {
    val user = newUser()
    populate(user)
    catchUp(user, OTHER)

    val revision = store.erase(user, ERASER, 42L, now)

    val caught = catchUp(user, OTHER)
    caught.nodes shouldHaveSize 2
    caught.edges shouldHaveSize 2
    caught.repertoires shouldHaveSize 1
    caught.tags shouldHaveSize 1
    caught.erasable.forEach {
      it.isDeleted shouldBe true
      it.originDevice shouldBe ERASER
      it.deviceSeq shouldBe 42L
    }
    (revision > 0L) shouldBe true
  }

  @Test
  fun eraseLeavesSettingsAlone() = runTest {
    val user = newUser()
    populate(user)
    catchUp(user, OTHER)

    store.erase(user, ERASER, 42L, now)

    catchUp(user, OTHER).settings.shouldBeEmpty()
    store.registerDevice(user, FRESH, DevicePlatform.JVM, afterReset = false, now)
    val stored = catchUp(user, FRESH).settings.single()
    stored.isDeleted shouldBe false
    stored.value shouldBe "dark"
  }

  @Test
  fun eraseStampsTombstonesAfterAFutureDatedRow() = runTest {
    val user = newUser()
    val future = fen("future")
    val past = fen("past")
    store.push(
      user,
      OTHER,
      SyncPushRequest(
        nodes = listOf(node(future, now + 5.seconds), node(past, now - 1.hours)),
        edges = emptyList(),
        settings = emptyList(),
        device = OTHER,
      ),
      now,
    )

    store.erase(user, ERASER, 42L, now)

    val byKey = catchUp(user, OTHER).nodes.associateBy { it.positionKey }
    byKey.getValue(future).updatedAt shouldBe now + 5.seconds + 1.milliseconds
    byKey.getValue(past).updatedAt shouldBe now
  }

  @Test
  fun theErasingDeviceIsServedItsOwnTombstones() = runTest {
    val user = newUser()
    populate(user)

    store.erase(user, ERASER, 42L, now)

    val caught = catchUp(user, ERASER)
    caught.erasable shouldHaveSize 6
    caught.erasable.forEach { it.isDeleted shouldBe true }
  }

  @Test
  fun aDeviceThatKeptItsRowsConvergesOnItsNextPull() = runTest {
    val user = newUser()
    val pushed = populate(user)
    catchUp(user, OTHER)

    store.erase(user, ERASER, 42L, now)

    val caught = catchUp(user, OTHER)
    caught.erasable shouldHaveSize 6
    caught.erasable.forEach { it.isDeleted shouldBe true }
    for (remote in caught.nodes) {
      val local = pushed.nodes.single { it.positionKey == remote.positionKey }
      resolve(local, remote).source shouldBe ResolutionSource.REMOTE
    }
    for (remote in caught.edges) {
      val local =
        pushed.edges.single { it.origin == remote.origin && it.destination == remote.destination }
      resolve(local, remote).source shouldBe ResolutionSource.REMOTE
    }
    resolve(pushed.repertoires.single(), caught.repertoires.single()).source shouldBe
      ResolutionSource.REMOTE
    resolve(pushed.tags.single(), caught.tags.single()).source shouldBe ResolutionSource.REMOTE
  }

  @Test
  fun eraseOfMoreThanOnePullPageIsServedAcrossPages() = runTest {
    val user = newUser()
    store.push(
      user,
      OTHER,
      SyncPushRequest(
        nodes = List(501) { node(fen("n$it")) },
        edges = emptyList(),
        settings = emptyList(),
        device = OTHER,
      ),
      now,
    )
    catchUp(user, OTHER)

    store.erase(user, ERASER, 42L, now)

    val nodes = catchUp(user, OTHER).nodes
    nodes shouldHaveSize 501
    nodes.forEach { it.isDeleted shouldBe true }
  }

  @Test
  fun erasingTwiceIsHarmless() = runTest {
    val user = newUser()
    populate(user)
    store.erase(user, ERASER, 42L, now)
    catchUp(user, OTHER)

    store.erase(user, ERASER, 43L, now) shouldBe 0L

    catchUp(user, OTHER).erasable.shouldBeEmpty()
  }

  @Test
  fun erasingAUserWithNoRowsReturnsZero() = runTest {
    store.erase(newUser(), ERASER, 42L, now) shouldBe 0L
  }

  @Test
  fun erasingOneUserLeavesAnotherUntouched() = runTest {
    val mine = newUser()
    val theirs = newUser()
    populate(mine)
    populate(theirs)

    store.erase(mine, ERASER, 42L, now)

    val caught = catchUp(theirs, OTHER)
    caught.erasable shouldHaveSize 6
    caught.erasable.forEach { it.isDeleted shouldBe false }
  }

  @Test
  fun anUnregisteredDeviceIsRefused() = runTest {
    val user = newUser()
    populate(user)

    shouldThrow<UnknownDeviceException> { store.erase(user, UNREGISTERED, 42L, now) }

    catchUp(user, OTHER).erasable.forEach { it.isDeleted shouldBe false }
  }

  private companion object {
    const val ERASER = "77777777-7777-4777-8777-777777777777"
    const val OTHER = "88888888-8888-4888-8888-888888888888"
    const val ERASE_ORIGIN = "00000000-0000-0000-0000-000000000000"
    const val FRESH = "55555555-5555-4555-8555-555555555555"
    const val UNREGISTERED = "66666666-6666-4666-8666-666666666666"
  }
}
