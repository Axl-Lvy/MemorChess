package proj.memorchess.axl.core.sync

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.InMemoryDatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.graph.InstalledMove
import proj.memorchess.axl.test_util.testSyncApplier
import proj.memorchess.axl.test_util.testTreeStore

/**
 * How [SyncApplier] derives a move's effective marking and existence from pulled holds and tags.
 */
class TestSyncApplierHolders {

  private val now = Instant.parse("2026-01-01T00:00:00Z")
  private val origin = "origin w K"
  private val destination = "destination b K"

  private fun node(key: String, deleted: Boolean = false, seq: Long = 1L) =
    NodeSyncRow(
      positionKey = key,
      dueDate = now,
      lastReview = null,
      firstReview = null,
      stability = 0.0,
      difficulty = 0.0,
      reps = 0,
      lapses = 0,
      phase = "NEW",
      step = 0,
      isDeleted = deleted,
      updatedAt = now,
      originDevice = "remote",
      deviceSeq = seq,
    )

  private fun tag(
    repertoireId: String,
    isGood: Boolean?,
    deleted: Boolean = false,
    seq: Long = 1L,
  ) =
    EdgeRepertoireTagSyncRow(
      origin = origin,
      destination = destination,
      repertoireId = repertoireId,
      move = "Ng3",
      isGood = isGood,
      isDeleted = deleted,
      updatedAt = now,
      originDevice = "remote",
      deviceSeq = seq,
    )

  private fun edge(isGood: Boolean, deleted: Boolean = false, seq: Long = 1L) =
    EdgeSyncRow(
      origin = origin,
      destination = destination,
      move = "Ng3",
      isGood = isGood,
      isDeleted = deleted,
      updatedAt = now,
      originDevice = "remote",
      deviceSeq = seq,
    )

  private suspend fun withBothNodes(): Pair<InMemoryDatabaseQueryManager, SyncApplier> {
    val database = InMemoryDatabaseQueryManager()
    val applier = testSyncApplier(database, CoroutineScope(Dispatchers.Unconfined))
    applier.applyNode(node(origin))
    applier.applyNode(node(destination))
    return database to applier
  }

  private suspend fun InMemoryDatabaseQueryManager.move(): DataMove? =
    getPositionIncludingDeleted(PositionKey(origin))?.previousAndNextMoves?.nextMoves?.get("Ng3")

  @Test
  fun aPulledTagCreatesAMoveTheDeviceHasNeverSeen() = runTest {
    val (database, applier) = withBothNodes()

    applier.applyTag(tag("a", isGood = false))

    val move = database.move()!!
    move.isGood shouldBe false
    move.ownIsGood shouldBe null
    move.isDeleted shouldBe false
  }

  @Test
  fun aMembershipTagCreatesNoMove() = runTest {
    val (database, applier) = withBothNodes()

    applier.applyTag(tag("own", isGood = null))

    database.move() shouldBe null
  }

  @Test
  fun aTagWhoseEndpointNodeIsMissingIsStoredWithoutCreatingTheMove() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val applier = testSyncApplier(database, CoroutineScope(Dispatchers.Unconfined))
    applier.applyNode(node(destination))

    val outcome = applier.applyTag(tag("a", isGood = true))

    outcome shouldBe ResolutionSource.REMOTE
    database.getTags(PositionKey(origin), PositionKey(destination)).single().repertoireId shouldBe
      "a"
    database.move() shouldBe null
  }

  @Test
  fun theOwnersExampleConverges() = runTest {
    val (database, applier) = withBothNodes()

    applier.applyTag(tag("a", isGood = true))
    applier.applyTag(tag("b", isGood = false))
    database.move()!!.isGood shouldBe true

    applier.applyTag(tag("a", isGood = true, deleted = true, seq = 2L))
    database.move()!!.isGood shouldBe false
  }

  @Test
  fun aPulledManualHoldBeatsInstallMarkings() = runTest {
    val (database, applier) = withBothNodes()
    applier.applyTag(tag("a", isGood = true))

    applier.applyMove(edge(isGood = false))

    database.move()!!.isGood shouldBe false
    database.move()!!.ownIsGood shouldBe false
  }

  @Test
  fun aPulledManualHoldTombstoneKeepsAMoveAnInstallStillHolds() = runTest {
    val (database, applier) = withBothNodes()
    applier.applyMove(edge(isGood = false))
    applier.applyTag(tag("a", isGood = true))

    applier.applyMove(edge(isGood = false, deleted = true, seq = 2L))

    val move = database.move()!!
    move.isDeleted shouldBe false
    move.isGood shouldBe true
    move.ownIsGood shouldBe null
  }

  @Test
  fun aPulledManualHoldWinsOverANeverHeldLocalMove() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val store = testTreeStore(database)
    val applier = testSyncApplier(database, CoroutineScope(Dispatchers.Unconfined))
    store.install(
      "a",
      listOf(InstalledMove(PositionKey(origin), "Ng3", PositionKey(destination), true, 0)),
    )

    val outcome = applier.applyMove(edge(isGood = false))

    outcome shouldBe ResolutionSource.REMOTE
    database.move()!!.ownIsGood shouldBe false
    database.move()!!.isGood shouldBe false
  }

  @Test
  fun aMoveLeftWithNoHolderIsDeleted() = runTest {
    val (database, applier) = withBothNodes()
    applier.applyMove(edge(isGood = true))

    applier.applyMove(edge(isGood = true, deleted = true, seq = 2L))

    database.move()!!.isDeleted shouldBe true
  }

  @Test
  fun aTagPulledBeforeItsOriginCreatesTheMoveOnceTheOriginArrives() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val applier = testSyncApplier(database, CoroutineScope(Dispatchers.Unconfined))
    applier.applyNode(node(destination))
    applier.applyTag(tag("a", isGood = true))

    applier.applyNode(node(origin))

    database.move()!!.isGood shouldBe true
    database.getPosition(PositionKey(origin))!!.hasGoodOutgoing shouldBe true
  }

  @Test
  fun aPulledHolderRevivesADeletedEndpointWithAFreshReviewState() = runTest {
    val (database, applier) = withBothNodes()
    applier.applyNode(node(destination, deleted = true, seq = 2L))

    applier.applyTag(tag("a", isGood = true))

    database.move()!!.isDeleted shouldBe false
    val revived = database.getPosition(PositionKey(destination))!!
    revived.cardState.reps shouldBe 0
  }

  @Test
  fun aPulledTagTombstoneDeletesAPositionLeftWithoutMoves() = runTest {
    val (database, applier) = withBothNodes()
    applier.applyTag(tag("a", isGood = true))

    applier.applyTag(tag("a", isGood = true, deleted = true, seq = 2L))

    database.getPosition(PositionKey(destination)) shouldBe null
    database.getPosition(PositionKey(origin)) shouldBe null
  }

  @Test
  fun aNodeTombstonePulledBeforeTheTagTombstoneLeavesNoOrphan() = runTest {
    val (database, applier) = withBothNodes()
    applier.applyTag(tag("a", isGood = true))
    applier.applyNode(node(destination, deleted = true, seq = 2L))

    applier.applyTag(tag("a", isGood = true, deleted = true, seq = 2L))

    database.getPosition(PositionKey(destination)) shouldBe null
  }

  @Test
  fun aPositionWithAnotherLiveMoveIsNotPruned() = runTest {
    val (database, applier) = withBothNodes()
    val third = "third w K"
    applier.applyNode(node(third))
    applier.applyMove(edge(isGood = true).copy(destination = third, move = "Nf3"))
    applier.applyTag(tag("a", isGood = true))

    applier.applyTag(tag("a", isGood = true, deleted = true, seq = 2L))

    database.getPosition(PositionKey(origin)) shouldNotBe null
    database.getPosition(PositionKey(destination)) shouldBe null
  }

  @Test
  fun theStartPositionIsNeverPruned() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val applier = testSyncApplier(database, CoroutineScope(Dispatchers.Unconfined))
    val start = PositionKey.START_POSITION.value
    applier.applyNode(node(start))
    applier.applyNode(node(destination))
    applier.applyTag(tag("a", isGood = true).copy(origin = start))

    applier.applyTag(tag("a", isGood = true, deleted = true, seq = 2L).copy(origin = start))

    database.getPosition(PositionKey.START_POSITION) shouldNotBe null
  }

  @Test
  fun aPulledNodeTombstoneKeepsAPositionAManualHoldStillReaches() = runTest {
    val (database, applier) = withBothNodes()
    applier.applyMove(edge(isGood = true))
    applier.applyTag(tag("a", isGood = true))
    applier.applyTag(tag("a", isGood = true, deleted = true, seq = 2L))

    applier.applyNode(node(destination, deleted = true, seq = 2L))

    database.getPosition(PositionKey(destination)) shouldNotBe null
    database.getPosition(PositionKey(destination))!!.cardState.reps shouldBe 0
    database.move()!!.isDeleted shouldBe false
  }
}
