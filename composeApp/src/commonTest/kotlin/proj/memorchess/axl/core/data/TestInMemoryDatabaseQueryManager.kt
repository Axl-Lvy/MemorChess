package proj.memorchess.axl.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.DeleteMode
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState
import proj.memorchess.axl.core.scheduling.CardStateFactory

/**
 * Direct branch coverage for [InMemoryDatabaseQueryManager]: insert/query, hard and soft deletes of
 * both positions and moves, incident-move stripping, erase, and the last-update aggregate.
 */
class TestInMemoryDatabaseQueryManager {

  // A three position line start -e4-> key1 -e5-> key2, with the matching incident moves.
  private val key0 = keyAfter()
  private val key1 = keyAfter("e4")
  private val key2 = keyAfter("e4", "e5")
  private val moveE4 = DataMove(key0, key1, "e4", isGood = true)
  private val moveE5 = DataMove(key1, key2, "e5", isGood = true)

  private fun keyAfter(vararg moves: String): PositionKey {
    val engine = GameEngine()
    moves.forEach { engine.playSanMove(it) }
    return engine.toPositionKey()
  }

  private fun node(
    key: PositionKey,
    previous: List<DataMove>,
    next: List<DataMove>,
    updatedAt: Instant = Instant.fromEpochSeconds(0),
  ) = DataNode(key, PreviousAndNextMoves(previous, next), CardStateFactory.new(), 0, updatedAt)

  private suspend fun seededLine(): InMemoryDatabaseQueryManager {
    val database = InMemoryDatabaseQueryManager()
    database.insertNodes(
      node(key0, previous = listOf(), next = listOf(moveE4)),
      node(key1, previous = listOf(moveE4), next = listOf(moveE5)),
      node(key2, previous = listOf(moveE5), next = listOf()),
    )
    return database
  }

  @Test
  fun insertsAndReadsBack() = runTest {
    val database = seededLine()
    assertEquals(3, database.getAllNodes().size)
    assertEquals(setOf("e5"), database.getPosition(key1)?.previousAndNextMoves?.nextMoves?.keys)
  }

  @Test
  fun getPositionReturnsNullForUnknownKey() = runTest {
    assertNull(seededLine().getPosition(keyAfter("d4")))
  }

  @Test
  fun hardDeletingAPositionStripsItsIncidentMoves() = runTest {
    val database = seededLine()
    database.deletePosition(key1, DeleteMode.HARD)
    assertNull(database.getPosition(key1))
    assertEquals(2, database.getAllNodes().size)
    // e4 pointed at key1, e5 came from key1: both are gone from the surviving neighbours.
    assertTrue(database.getPosition(key0)!!.previousAndNextMoves.nextMoves.isEmpty())
    assertTrue(database.getPosition(key2)!!.previousAndNextMoves.previousMoves.isEmpty())
  }

  @Test
  fun hardDeletingALeafKeepsUnrelatedMovesOfOtherNodes() = runTest {
    val database = seededLine()
    database.deletePosition(key2, DeleteMode.HARD)
    assertNull(database.getPosition(key2))
    // key0 never referenced key2, so its e4 move (to key1) survives the cascade untouched.
    assertEquals(setOf("e4"), database.getPosition(key0)!!.previousAndNextMoves.nextMoves.keys)
    // key1 keeps its incoming e4 but loses its outgoing e5, which pointed at the removed key2.
    assertEquals(setOf("e4"), database.getPosition(key1)!!.previousAndNextMoves.previousMoves.keys)
    assertTrue(database.getPosition(key1)!!.previousAndNextMoves.nextMoves.isEmpty())
  }

  @Test
  fun softDeletingAPositionHidesItButKeepsTheRow() = runTest {
    val database = seededLine()
    database.deletePosition(key1, DeleteMode.SOFT)
    assertNull(database.getPosition(key1))
    assertEquals(2, database.getAllNodes().size)
    val withDeleted = database.getAllNodes(withDeletedOnes = true)
    assertEquals(3, withDeleted.size)
    assertTrue(withDeleted.single { it.positionKey == key1 }.isDeleted)
  }

  @Test
  fun deletingAMissingPositionIsANoOp() = runTest {
    val database = seededLine()
    database.deletePosition(keyAfter("d4"), DeleteMode.HARD)
    assertEquals(3, database.getAllNodes().size)
  }

  @Test
  fun hardDeletingAMoveRemovesItFromBothEnds() = runTest {
    val database = seededLine()
    database.deleteMove(key1, "e5", DeleteMode.HARD)
    assertTrue(database.getPosition(key1)!!.previousAndNextMoves.nextMoves.isEmpty())
    assertTrue(database.getPosition(key2)!!.previousAndNextMoves.previousMoves.isEmpty())
  }

  @Test
  fun softDeletingAMoveFlagsItOnBothEnds() = runTest {
    val database = seededLine()
    database.deleteMove(key1, "e5", DeleteMode.SOFT)
    assertTrue(database.getPosition(key1)!!.previousAndNextMoves.nextMoves.getValue("e5").isDeleted)
    assertTrue(
      database.getPosition(key2)!!.previousAndNextMoves.previousMoves.getValue("e5").isDeleted
    )
  }

  @Test
  fun deletingAMissingMoveIsANoOp() = runTest {
    val database = seededLine()
    database.deleteMove(key0, "Qh5", DeleteMode.HARD)
    database.deleteMove(keyAfter("d4"), "d5", DeleteMode.HARD)
    assertEquals(setOf("e4"), database.getPosition(key0)!!.previousAndNextMoves.nextMoves.keys)
  }

  @Test
  fun deletingAMoveWhoseDestinationIsAbsentUpdatesOnlyTheOrigin() = runTest {
    val database = InMemoryDatabaseQueryManager()
    // Only the origin exists; its e4 edge points at a key1 node that was never inserted.
    database.insertNodes(node(key0, previous = listOf(), next = listOf(moveE4)))
    database.deleteMove(key0, "e4", DeleteMode.HARD)
    assertTrue(database.getPosition(key0)!!.previousAndNextMoves.nextMoves.isEmpty())
    assertNull(database.getPosition(key1))
  }

  @Test
  fun hardDeletingOneOfSeveralMovesKeepsTheOthers() = runTest {
    val keyD4 = keyAfter("d4")
    val moveD4 = DataMove(key0, keyD4, "d4", isGood = true)
    val database = InMemoryDatabaseQueryManager()
    database.insertNodes(
      node(key0, previous = listOf(), next = listOf(moveE4, moveD4)),
      node(key1, previous = listOf(moveE4), next = listOf()),
      node(keyD4, previous = listOf(moveD4), next = listOf()),
    )
    database.deleteMove(key0, "e4", DeleteMode.HARD)
    // d4 is left in place; only the matching e4 is removed from the origin.
    assertEquals(setOf("d4"), database.getPosition(key0)!!.previousAndNextMoves.nextMoves.keys)
    assertTrue(database.getPosition(key1)!!.previousAndNextMoves.previousMoves.isEmpty())
  }

  @Test
  fun eraseAllClearsEverything() = runTest {
    val database = seededLine()
    database.eraseAll()
    assertTrue(database.getAllNodes(withDeletedOnes = true).isEmpty())
  }

  @Test
  fun lastUpdateIsNullWhenEmpty() = runTest {
    assertNull(InMemoryDatabaseQueryManager().getLastUpdate())
  }

  @Test
  fun lastUpdateIsTheLatestOfNodesAndMoves() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val moveStamp = Instant.fromEpochSeconds(500)
    val nodeStamp = Instant.fromEpochSeconds(200)
    val recentMove = DataMove(key0, key1, "e4", isGood = true, updatedAt = moveStamp)
    database.insertNodes(
      node(key0, previous = listOf(), next = listOf(recentMove), updatedAt = nodeStamp)
    )
    // The move is newer than its node, so it drives the aggregate.
    assertEquals(moveStamp, database.getLastUpdate())
  }

  @Test
  fun lastUpdateFallsBackToNodeStampWhenItIsLatest() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val nodeStamp = Instant.fromEpochSeconds(900)
    database.insertNodes(node(key0, previous = listOf(), next = listOf(), updatedAt = nodeStamp))
    assertEquals(nodeStamp, database.getLastUpdate())
  }

  // --- Bounded scheduling query surface ---

  private val now = Instant.fromEpochSeconds(1_000)
  private val dayStart = Instant.fromEpochSeconds(0)
  private val dayEnd = Instant.fromEpochSeconds(10_000)

  /** Builds a scheduling node with explicit projection columns and an isolated synthetic key. */
  private fun schedNode(
    keyName: String,
    phase: CardPhase,
    dueDate: Instant,
    hasGoodOutgoing: Boolean = true,
    depth: Int = 0,
    createdAt: Instant = Instant.fromEpochSeconds(0),
    lastReview: Instant? = null,
    firstReview: Instant? = null,
    isDeleted: Boolean = false,
  ): DataNode =
    DataNode(
      positionKey = PositionKey(keyName),
      previousAndNextMoves = PreviousAndNextMoves(),
      cardState =
        CardState(
          dueDate = dueDate,
          lastReview = lastReview,
          firstReview = firstReview,
          stability = 0.0,
          difficulty = 0.0,
          reps = 0,
          lapses = 0,
          phase = phase,
          step = 0,
        ),
      depth = depth,
      isDeleted = isDeleted,
      hasGoodOutgoing = hasGoodOutgoing,
      createdAt = createdAt,
    )

  @Test
  fun nextDueReviewCard_picksSmallestDepthAmongDueGoodReviews() = runTest {
    val database = InMemoryDatabaseQueryManager()
    database.insertNodes(
      schedNode("deep", CardPhase.REVIEW, dueDate = Instant.fromEpochSeconds(100), depth = 5),
      schedNode("shallow", CardPhase.REVIEW, dueDate = Instant.fromEpochSeconds(200), depth = 1),
      // Not good: excluded even though shallowest and due.
      schedNode(
        "notgood",
        CardPhase.REVIEW,
        dueDate = Instant.fromEpochSeconds(50),
        depth = 0,
        hasGoodOutgoing = false,
      ),
      // Wrong phase.
      schedNode("new", CardPhase.NEW, dueDate = Instant.fromEpochSeconds(50), depth = 0),
    )
    assertEquals(PositionKey("shallow"), database.nextDueReviewCard(dayEnd)?.positionKey)
  }

  @Test
  fun nextDueReviewCard_excludesDueDateEqualToDayEnd() = runTest {
    val database = InMemoryDatabaseQueryManager()
    // dueDate exactly at the exclusive bound belongs to the next day.
    database.insertNodes(schedNode("boundary", CardPhase.REVIEW, dueDate = dayEnd, depth = 0))
    assertNull(database.nextDueReviewCard(dayEnd))
  }

  @Test
  fun nextDueNewCard_ordersByDepthThenCreatedAt() = runTest {
    val database = InMemoryDatabaseQueryManager()
    database.insertNodes(
      schedNode(
        "d1late",
        CardPhase.NEW,
        dueDate = Instant.fromEpochSeconds(100),
        depth = 1,
        createdAt = Instant.fromEpochSeconds(500),
      ),
      schedNode(
        "d1early",
        CardPhase.NEW,
        dueDate = Instant.fromEpochSeconds(100),
        depth = 1,
        createdAt = Instant.fromEpochSeconds(300),
      ),
      schedNode(
        "d0",
        CardPhase.NEW,
        dueDate = Instant.fromEpochSeconds(100),
        depth = 0,
        createdAt = Instant.fromEpochSeconds(900),
      ),
    )
    // Shallowest wins regardless of createdAt.
    assertEquals(PositionKey("d0"), database.nextDueNewCard(dayEnd)?.positionKey)
    // Among the depth-1 ties, the earlier createdAt wins once d0 is removed.
    database.deletePosition(PositionKey("d0"), DeleteMode.HARD)
    assertEquals(PositionKey("d1early"), database.nextDueNewCard(dayEnd)?.positionKey)
  }

  @Test
  fun nextReadyLearningCard_includesDueEqualToNow() = runTest {
    val database = InMemoryDatabaseQueryManager()
    database.insertNodes(schedNode("atNow", CardPhase.LEARNING, dueDate = now))
    assertEquals(PositionKey("atNow"), database.nextReadyLearningCard(now)?.positionKey)
  }

  @Test
  fun nextReadyLearningCard_picksEarliestDueAndCoversRelearning() = runTest {
    val database = InMemoryDatabaseQueryManager()
    database.insertNodes(
      schedNode("late", CardPhase.LEARNING, dueDate = Instant.fromEpochSeconds(900)),
      schedNode("early", CardPhase.RELEARNING, dueDate = Instant.fromEpochSeconds(100)),
      // Pending (due in the future) must not be picked as ready.
      schedNode("future", CardPhase.LEARNING, dueDate = Instant.fromEpochSeconds(5_000)),
    )
    assertEquals(PositionKey("early"), database.nextReadyLearningCard(now)?.positionKey)
  }

  @Test
  fun nextPendingLearningCard_excludesDueEqualToNow() = runTest {
    val database = InMemoryDatabaseQueryManager()
    database.insertNodes(
      schedNode("atNow", CardPhase.LEARNING, dueDate = now),
      schedNode("future", CardPhase.RELEARNING, dueDate = Instant.fromEpochSeconds(2_000)),
    )
    // Strictly greater than now: the card due exactly at now is ready, not pending.
    assertEquals(PositionKey("future"), database.nextPendingLearningCard(now)?.positionKey)
  }

  @Test
  fun nextPendingLearningCard_isNullWhenAllReady() = runTest {
    val database = InMemoryDatabaseQueryManager()
    database.insertNodes(schedNode("ready", CardPhase.LEARNING, dueDate = now))
    assertNull(database.nextPendingLearningCard(now))
  }

  @Test
  fun getSchedulingCounts_countsAreDayBounded() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val inWindow = Instant.fromEpochSeconds(5_000)
    database.insertNodes(
      // introducedToday: firstReview in window; also trained today.
      schedNode(
        "introduced",
        CardPhase.REVIEW,
        dueDate = Instant.fromEpochSeconds(100),
        firstReview = inWindow,
        lastReview = inWindow,
        depth = 0,
      ),
      // trainedToday only: lastReview in window, firstReview before window.
      schedNode(
        "trainedOnly",
        CardPhase.REVIEW,
        dueDate = Instant.fromEpochSeconds(200),
        firstReview = Instant.fromEpochSeconds(-100),
        lastReview = inWindow,
        depth = 1,
      ),
      // firstReview at the exclusive upper bound: outside the window.
      schedNode(
        "atBoundary",
        CardPhase.REVIEW,
        dueDate = Instant.fromEpochSeconds(300),
        firstReview = dayEnd,
        lastReview = dayEnd,
        depth = 2,
      ),
      // due new, good.
      schedNode("dueNew", CardPhase.NEW, dueDate = Instant.fromEpochSeconds(400), depth = 3),
      // in session.
      schedNode("learning", CardPhase.LEARNING, dueDate = Instant.fromEpochSeconds(500), depth = 4),
    )
    val counts = database.getSchedulingCounts(dayStart, dayEnd)
    assertEquals(1, counts.introducedToday)
    assertEquals(2, counts.trainedToday)
    // dueReviews: introduced, trainedOnly, atBoundary all REVIEW and due before dayEnd.
    assertEquals(3, counts.dueReviews)
    assertEquals(1, counts.dueNew)
    assertEquals(1, counts.inSession)
  }

  @Test
  fun getSchedulingCounts_emptyStoreIsAllZero() = runTest {
    val counts = InMemoryDatabaseQueryManager().getSchedulingCounts(dayStart, dayEnd)
    assertEquals(SchedulingCounts(0, 0, 0, 0, 0), counts)
  }

  @Test
  fun findEligibleAmong_returnsFirstEligibleKeyOrNull() = runTest {
    val database = InMemoryDatabaseQueryManager()
    database.insertNodes(
      // Not in the candidate set even though eligible.
      schedNode("other", CardPhase.LEARNING, dueDate = now),
      // In set, in session: eligible.
      schedNode("learning", CardPhase.LEARNING, dueDate = Instant.fromEpochSeconds(9_999)),
      // In set, REVIEW due: eligible.
      schedNode("dueReview", CardPhase.REVIEW, dueDate = Instant.fromEpochSeconds(100)),
      // In set but not due and not in session: ineligible.
      schedNode("notDue", CardPhase.REVIEW, dueDate = dayEnd),
    )
    val candidates =
      listOf(PositionKey("learning"), PositionKey("dueReview"), PositionKey("notDue"))
    val result = database.findEligibleAmong(candidates, dayEnd)
    assertEquals(PositionKey("learning"), result?.positionKey)
    // Empty candidate set yields null.
    assertNull(database.findEligibleAmong(emptyList(), dayEnd))
    // None eligible yields null.
    assertNull(database.findEligibleAmong(listOf(PositionKey("notDue")), dayEnd))
  }
}
