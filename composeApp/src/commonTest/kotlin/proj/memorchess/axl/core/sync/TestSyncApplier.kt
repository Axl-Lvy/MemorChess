package proj.memorchess.axl.core.sync

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.graph.TrainableProjection
import proj.memorchess.axl.test_util.GatingDatabaseQueryManager
import proj.memorchess.axl.test_util.testNodeCache
import proj.memorchess.axl.test_util.testSyncApplier

/** Tests for [SyncApplier]'s resolution of pulled rows against their local copies. */
@OptIn(ExperimentalCoroutinesApi::class)
class TestSyncApplier {

  private val now = Instant.parse("2026-01-01T00:00:00Z")

  private fun applier(database: InMemoryDatabaseQueryManager = InMemoryDatabaseQueryManager()) =
    testSyncApplier(database, CoroutineScope(Dispatchers.Unconfined))

  private fun row(
    key: String,
    deviceSeq: Long = 1L,
    originDevice: String = "remote",
    dueDate: Instant = now,
  ) =
    NodeSyncRow(
      positionKey = key,
      dueDate = dueDate,
      lastReview = null,
      firstReview = null,
      stability = 0.0,
      difficulty = 0.0,
      reps = 0,
      lapses = 0,
      phase = "NEW",
      step = 0,
      isDeleted = false,
      updatedAt = now,
      originDevice = originDevice,
      deviceSeq = deviceSeq,
    )

  @Test
  fun applyNodeOnANewPositionWrites() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val applier = applier(database)

    val outcome = applier.applyNode(row("start"))

    outcome shouldBe ResolutionSource.REMOTE
    database.getPosition(PositionKey("start"))?.positionKey shouldBe PositionKey("start")
  }

  @Test
  fun applyNodeLocalWinsSkipsTheWrite() = runTest {
    val applier = applier()
    // A local row from the same origin device as the incoming remote row, with a higher deviceSeq.
    applier.applyNode(row("start", deviceSeq = 5L))

    val outcome = applier.applyNode(row("start", deviceSeq = 1L))

    outcome shouldBe ResolutionSource.LOCAL
  }

  @Test
  fun applyNodeEvictsTheCachedEntrySoTheNextReadSeesTheWrite() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val cache = testNodeCache(database, CoroutineScope(Dispatchers.Unconfined))
    val applier = SyncApplier(database, cache, TrainableProjection(database, cache))
    val laterDueDate = Instant.parse("2026-06-01T00:00:00Z")
    applier.applyNode(row("start", deviceSeq = 1L, dueDate = now))
    cache.resolve(PositionKey("start")) // warms the cache with the deviceSeq=1 due date

    applier.applyNode(row("start", deviceSeq = 2L, dueDate = laterDueDate))

    // If the invalidation did not happen this would still read the deviceSeq=1 cached due date.
    cache.resolve(PositionKey("start")).node?.cardState?.dueDate shouldBe laterDueDate
  }

  @Test
  fun applyMoveOnExistingEndpointsWritesAndFlipsHasGoodOutgoing() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val cache = testNodeCache(database, CoroutineScope(Dispatchers.Unconfined))
    val applier = SyncApplier(database, cache, TrainableProjection(database, cache))
    applier.applyNode(row("a"))
    applier.applyNode(row("b"))

    val outcome =
      applier.applyMove(
        EdgeSyncRow(
          origin = "a",
          destination = "b",
          move = "e4",
          isGood = true,
          isDeleted = false,
          updatedAt = now,
          originDevice = "remote",
          deviceSeq = 1L,
        )
      )

    outcome shouldBe ResolutionSource.REMOTE
    cache.resolve(PositionKey("a")).node?.outgoing?.keys shouldBe setOf("e4")
  }

  @Test
  fun anAppliedRemoteNodeIsNotResurrectedByAnInFlightLoad() = runTest {
    val positionKey = PositionKey("start")
    val laterDueDate = Instant.parse("2026-06-01T00:00:00Z")
    val database = InMemoryDatabaseQueryManager()
    val gating = GatingDatabaseQueryManager(database, positionKey)
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
    val cache = testNodeCache(gating, scope)
    val applier = SyncApplier(database, cache, TrainableProjection(database, cache))
    applier.applyNode(row("start", deviceSeq = 1L, dueDate = now))

    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { cache.resolve(positionKey) }
    applier.applyNode(row("start", deviceSeq = 2L, dueDate = laterDueDate)) shouldBe
      ResolutionSource.REMOTE
    gating.gate.complete(Unit)

    // The load started before the apply and resumed after it. It must not have put the pre sync row
    // back over the applied one.
    cache.peek(positionKey)?.cardState?.dueDate shouldBe laterDueDate
  }
}
