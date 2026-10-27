package proj.memorchess.axl.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.test_util.drainAllNodes

/** Branch coverage for [InMemoryDatabaseQueryManager]. */
class TestInMemoryDatabaseQueryManager {

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
    assertEquals(3, drainAllNodes(database).size)
    assertEquals(setOf("e5"), database.getPosition(key1)?.previousAndNextMoves?.nextMoves?.keys)
  }

  @Test
  fun getPositionReturnsNullForUnknownKey() = runTest {
    assertNull(seededLine().getPosition(keyAfter("d4")))
  }

  @Test
  fun softDeletingALeafKeepsUnrelatedMovesOfOtherNodesLive() = runTest {
    val database = seededLine()
    database.deletePosition(key2)
    assertNull(database.getPosition(key2))
    assertFalse(
      database.getPosition(key0)!!.previousAndNextMoves.nextMoves.getValue("e4").isDeleted
    )
    val key1Moves = database.getPosition(key1)!!.previousAndNextMoves
    assertFalse(key1Moves.previousMoves.getValue("e4").isDeleted)
    assertTrue(key1Moves.nextMoves.getValue("e5").isDeleted)
  }

  @Test
  fun softDeletingAPositionHidesItFromReads() = runTest {
    val database = seededLine()
    database.deletePosition(key1)
    assertNull(database.getPosition(key1))
    assertEquals(2, drainAllNodes(database).size)
  }

  @Test
  fun deletingAMissingPositionIsANoOp() = runTest {
    val database = seededLine()
    database.deletePosition(keyAfter("d4"))
    assertEquals(3, drainAllNodes(database).size)
  }

  @Test
  fun softDeletingAMoveFlagsItOnBothEnds() = runTest {
    val database = seededLine()
    database.deleteMove(key1, "e5")
    assertTrue(database.getPosition(key1)!!.previousAndNextMoves.nextMoves.getValue("e5").isDeleted)
    assertTrue(
      database.getPosition(key2)!!.previousAndNextMoves.previousMoves.getValue("e5").isDeleted
    )
  }

  @Test
  fun deletingAMissingMoveIsANoOp() = runTest {
    val database = seededLine()
    database.deleteMove(key0, "Qh5")
    database.deleteMove(keyAfter("d4"), "d5")
    assertEquals(setOf("e4"), database.getPosition(key0)!!.previousAndNextMoves.nextMoves.keys)
  }

  @Test
  fun deletingAMoveWhoseDestinationIsAbsentUpdatesOnlyTheOrigin() = runTest {
    val database = InMemoryDatabaseQueryManager()
    database.insertNodes(node(key0, previous = listOf(), next = listOf(moveE4)))
    database.deleteMove(key0, "e4")
    assertTrue(database.getPosition(key0)!!.previousAndNextMoves.nextMoves.getValue("e4").isDeleted)
    assertNull(database.getPosition(key1))
  }

  @Test
  fun softDeletingOneOfSeveralMovesKeepsTheOthersLive() = runTest {
    val keyD4 = keyAfter("d4")
    val moveD4 = DataMove(key0, keyD4, "d4", isGood = true)
    val database = InMemoryDatabaseQueryManager()
    database.insertNodes(
      node(key0, previous = listOf(), next = listOf(moveE4, moveD4)),
      node(key1, previous = listOf(moveE4), next = listOf()),
      node(keyD4, previous = listOf(moveD4), next = listOf()),
    )
    database.deleteMove(key0, "e4")
    val originMoves = database.getPosition(key0)!!.previousAndNextMoves.nextMoves
    assertFalse(originMoves.getValue("d4").isDeleted)
    assertTrue(originMoves.getValue("e4").isDeleted)
    assertTrue(
      database.getPosition(key1)!!.previousAndNextMoves.previousMoves.getValue("e4").isDeleted
    )
  }

  @Test
  fun eraseAllClearsEverything() = runTest {
    val database = seededLine()
    database.eraseAll()
    assertTrue(drainAllNodes(database).isEmpty())
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
    assertEquals(moveStamp, database.getLastUpdate())
  }

  @Test
  fun lastUpdateFallsBackToNodeStampWhenItIsLatest() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val nodeStamp = Instant.fromEpochSeconds(900)
    database.insertNodes(node(key0, previous = listOf(), next = listOf(), updatedAt = nodeStamp))
    assertEquals(nodeStamp, database.getLastUpdate())
  }

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
      schedNode(
        "notgood",
        CardPhase.REVIEW,
        dueDate = Instant.fromEpochSeconds(50),
        depth = 0,
        hasGoodOutgoing = false,
      ),
      schedNode("new", CardPhase.NEW, dueDate = Instant.fromEpochSeconds(50), depth = 0),
    )
    assertEquals(PositionKey("shallow"), database.nextDueReviewCard(dayEnd)?.positionKey)
  }

  @Test
  fun nextDueReviewCard_excludesDueDateEqualToDayEnd() = runTest {
    val database = InMemoryDatabaseQueryManager()
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
    assertEquals(PositionKey("d0"), database.nextDueNewCard(dayEnd)?.positionKey)
    database.deletePosition(PositionKey("d0"))
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
      schedNode(
        "introduced",
        CardPhase.REVIEW,
        dueDate = Instant.fromEpochSeconds(100),
        firstReview = inWindow,
        lastReview = inWindow,
        depth = 0,
      ),
      schedNode(
        "trainedOnly",
        CardPhase.REVIEW,
        dueDate = Instant.fromEpochSeconds(200),
        firstReview = Instant.fromEpochSeconds(-100),
        lastReview = inWindow,
        depth = 1,
      ),
      schedNode(
        "atBoundary",
        CardPhase.REVIEW,
        dueDate = Instant.fromEpochSeconds(300),
        firstReview = dayEnd,
        lastReview = dayEnd,
        depth = 2,
      ),
      schedNode("dueNew", CardPhase.NEW, dueDate = Instant.fromEpochSeconds(400), depth = 3),
      schedNode("learning", CardPhase.LEARNING, dueDate = Instant.fromEpochSeconds(500), depth = 4),
    )
    val counts = database.getSchedulingCounts(dayStart, dayEnd)
    assertEquals(1, counts.introducedToday)
    assertEquals(2, counts.trainedToday)
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
      schedNode("other", CardPhase.LEARNING, dueDate = now),
      schedNode("learning", CardPhase.LEARNING, dueDate = Instant.fromEpochSeconds(9_999)),
      schedNode("dueReview", CardPhase.REVIEW, dueDate = Instant.fromEpochSeconds(100)),
      schedNode("notDue", CardPhase.REVIEW, dueDate = dayEnd),
    )
    val candidates =
      listOf(PositionKey("learning"), PositionKey("dueReview"), PositionKey("notDue"))
    val result = database.findEligibleAmong(candidates, dayEnd)
    assertEquals(PositionKey("learning"), result?.positionKey)
    assertNull(database.findEligibleAmong(emptyList(), dayEnd))
    assertNull(database.findEligibleAmong(listOf(PositionKey("notDue")), dayEnd))
  }

  private fun countNode(key: PositionKey, previous: List<DataMove>, next: List<DataMove>) =
    DataNode(key, PreviousAndNextMoves(previous, next), CardStateFactory.new())

  @Test
  fun countDescendantsOfUnknownKeyIsZero() = runTest {
    assertEquals(0, InMemoryDatabaseQueryManager().countDescendants(key0))
  }

  @Test
  fun countDescendantsLeafIsOne() = runTest {
    val database = InMemoryDatabaseQueryManager()
    database.insertNodes(countNode(key0, previous = listOf(), next = listOf()))
    assertEquals(1, database.countDescendants(key0))
  }

  @Test
  fun countDescendantsSingleChildIsTwo() = runTest {
    val database = InMemoryDatabaseQueryManager()
    database.insertNodes(
      countNode(key0, previous = listOf(), next = listOf(moveE4)),
      countNode(key1, previous = listOf(moveE4), next = listOf()),
    )
    assertEquals(2, database.countDescendants(key0))
  }

  @Test
  fun countDescendantsLinearChainCountsEveryNode() = runTest {
    assertEquals(3, seededLine().countDescendants(key0))
    assertEquals(2, seededLine().countDescendants(key1))
    assertEquals(1, seededLine().countDescendants(key2))
  }

  @Test
  fun countDescendantsStopsAtConvergentNode() = runTest {
    // posB is a second parent of key2, so deleting key0's subtree keeps key2.
    val posB = keyAfter("d4")
    val d4 = DataMove(posB, key2, "d4", isGood = true)
    val database = InMemoryDatabaseQueryManager()
    database.insertNodes(
      countNode(key0, previous = listOf(), next = listOf(moveE4)),
      countNode(key1, previous = listOf(moveE4), next = listOf(moveE5)),
      countNode(key2, previous = listOf(moveE5, d4), next = listOf()),
      countNode(posB, previous = listOf(), next = listOf(d4)),
    )
    assertEquals(2, database.countDescendants(key0))
  }

  private suspend fun DatabaseQueryManager.pageAllKeys(limit: Int): List<PositionKey> {
    val visited = mutableListOf<PositionKey>()
    var cursor: String? = null
    while (true) {
      val page = getNodesPage(cursor, limit)
      visited += page.nodes.map { it.positionKey }
      cursor = page.nextCursor ?: break
    }
    return visited
  }

  /** Inserts [count] nodes with sortable keys and returns the keys ascending. */
  private suspend fun InMemoryDatabaseQueryManager.seedPageable(count: Int): List<PositionKey> {
    val keys = (0 until count).map { PositionKey("page${it.toString().padStart(3, '0')}") }
    insertNodes(*keys.map { countNode(it, previous = listOf(), next = listOf()) }.toTypedArray())
    return keys.sortedBy { it.value }
  }

  @Test
  fun getNodesPage_pagingTheWholeStoreVisitsEveryNodeExactlyOnce() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val expected = database.seedPageable(7)
    // A page size that does not divide the store size exercises a non-empty final page.
    val visited = database.pageAllKeys(limit = 3)
    assertEquals(expected, visited)
    assertEquals(7, visited.toSet().size)
  }

  @Test
  fun getNodesPage_lastPageHasNullNextCursor() = runTest {
    val database = InMemoryDatabaseQueryManager()
    database.seedPageable(2)
    val first = database.getNodesPage(cursor = null, limit = 2)
    // A full page still advertises a cursor; only the empty follow-up page ends with null.
    assertEquals(2, first.nodes.size)
    assertEquals(
      database.getPosition(first.nodes.last().positionKey)!!.positionKey.value,
      first.nextCursor,
    )
    val second = database.getNodesPage(cursor = first.nextCursor, limit = 2)
    assertTrue(second.nodes.isEmpty())
    assertNull(second.nextCursor)
  }

  @Test
  fun getNodesPage_limitLargerThanStoreReturnsEverythingWithNullCursor() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val expected = database.seedPageable(3)
    val page = database.getNodesPage(cursor = null, limit = 100)
    assertEquals(expected, page.nodes.map { it.positionKey })
    assertNull(page.nextCursor)
  }

  @Test
  fun getNodesPage_storeSizeExactMultipleOfLimitTerminatesOnEmptyPage() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val expected = database.seedPageable(6)
    val visited = database.pageAllKeys(limit = 2)
    assertEquals(expected, visited)
  }

  @Test
  fun getNodesPage_emptyStoreReturnsEmptyPageWithNullCursor() = runTest {
    val page = InMemoryDatabaseQueryManager().getNodesPage(cursor = null, limit = 5)
    assertTrue(page.nodes.isEmpty())
    assertNull(page.nextCursor)
  }

  @Test
  fun getNodesPage_carriesEdgesLikeASingleRead() = runTest {
    val database = seededLine()
    val byKey = database.pageAll(limit = 1).associateBy { it.positionKey }
    assertEquals(setOf("e4"), byKey.getValue(key0).previousAndNextMoves.nextMoves.keys)
    assertEquals(setOf("e4"), byKey.getValue(key1).previousAndNextMoves.previousMoves.keys)
    assertEquals(setOf("e5"), byKey.getValue(key1).previousAndNextMoves.nextMoves.keys)
  }

  @Test
  fun getNodesPage_excludesSoftDeletedRows() = runTest {
    val database = seededLine()
    database.deletePosition(key1)
    val keys = database.pageAllKeys(limit = 1)
    assertEquals(2, keys.size)
    assertTrue(key1 !in keys)
  }

  @Test
  fun getNodesPage_rejectsNonPositiveLimit() = runTest {
    val database = InMemoryDatabaseQueryManager()
    assertFailsWith<IllegalArgumentException> { database.getNodesPage(cursor = null, limit = 0) }
    assertFailsWith<IllegalArgumentException> { database.getNodesPage(cursor = null, limit = -1) }
  }

  private suspend fun DatabaseQueryManager.pageAll(limit: Int): List<DataNode> {
    val visited = mutableListOf<DataNode>()
    var cursor: String? = null
    while (true) {
      val page = getNodesPage(cursor, limit)
      visited += page.nodes
      cursor = page.nextCursor ?: break
    }
    return visited
  }

  @Test
  fun countDescendantsIsCappedAtTheRequestedBound() = runTest {
    val keys = (0 until 10).map { PositionKey("chain$it x K") }
    val moves = (0 until 9).map { DataMove(keys[it], keys[it + 1], "m$it", isGood = true) }
    val database = InMemoryDatabaseQueryManager()
    val nodes = keys.mapIndexed { index, key ->
      val incoming = if (index == 0) emptyList() else listOf(moves[index - 1])
      val outgoing = if (index == 9) emptyList() else listOf(moves[index])
      countNode(key, previous = incoming, next = outgoing)
    }
    database.insertNodes(*nodes.toTypedArray())
    val root = keys[0]
    assertEquals(4, database.countDescendants(root, cap = 4))
    assertEquals(5, database.countDescendants(root, cap = 5))
    assertEquals(6, database.countDescendants(root, cap = 6))
    assertEquals(10, database.countDescendants(root, cap = 100))
    assertEquals(0, database.countDescendants(root, cap = 0))
  }

  @Test
  fun softDeletingAPositionBumpsUpdatedAt() = runTest {
    val database = seededLine()
    // Fixture moves are stamped DateUtil.now(), so the tombstone must be later to be the maximum.
    val stamp = proj.memorchess.axl.core.date.DateUtil.now() + kotlin.time.Duration.parse("P365D")
    database.deletePosition(key1, "device-a", 5L, stamp)
    assertEquals(stamp, database.getLastUpdate())
  }

  @Test
  fun softDeletingAMoveStampsOriginDeviceAndDeviceSeqOnTheSurvivingEndpoint() = runTest {
    val database = seededLine()
    val stamp = Instant.fromEpochSeconds(999_999)
    database.deleteMove(key1, "e5", "device-a", 5L, stamp)
    val tombstone = database.getPosition(key1)!!.previousAndNextMoves.nextMoves.getValue("e5")
    assertTrue(tombstone.isDeleted)
    assertEquals("device-a", tombstone.originDevice)
    assertEquals(5L, tombstone.deviceSeq)
    assertEquals(stamp, tombstone.updatedAt)
  }

  @Test
  fun markDirtyThenGetOutboxReturnsTheKey() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val key = DirtyKey.NodeKey(key1)
    database.markDirty(key, 1L)
    assertEquals(listOf(OutboxEntry(key, 1L)), database.getOutbox())
  }

  @Test
  fun markingTheSameKeyDirtyTwiceCollapsesToOneOutboxEntry() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val key = DirtyKey.NodeKey(key1)
    database.markDirty(key, 1L)
    database.markDirty(key, 2L)
    assertEquals(listOf(OutboxEntry(key, 2L)), database.getOutbox())
  }

  @Test
  fun markingTheSameKeyWithALowerSequenceDoesNotRegressTheStoredOne() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val key = DirtyKey.NodeKey(key1)
    database.markDirty(key, 5L)
    database.markDirty(key, 1L)
    assertEquals(listOf(OutboxEntry(key, 5L)), database.getOutbox())
  }

  @Test
  fun clearDirtyRemovesExactlyTheClearedKeys() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val kept = DirtyKey.NodeKey(key1)
    val cleared = DirtyKey.NodeKey(key2)
    database.markDirty(kept, 1L)
    database.markDirty(cleared, 1L)
    database.clearDirty(listOf(OutboxEntry(cleared, 1L)))
    assertEquals(listOf(OutboxEntry(kept, 1L)), database.getOutbox())
  }

  @Test
  fun clearDirtySurvivesAMarkThatLandsAfterTheEntryWasRead() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val key = DirtyKey.NodeKey(key1)
    database.markDirty(key, 1L)
    val read = database.getOutbox()
    database.markDirty(key, 2L)
    database.clearDirty(read)
    assertEquals(listOf(OutboxEntry(key, 2L)), database.getOutbox())
  }

  @Test
  fun getOutboxOrdersEntriesByAscendingDeviceSeq() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val first = DirtyKey.NodeKey(key0)
    val second = DirtyKey.NodeKey(key1)
    database.markDirty(second, 9L)
    database.markDirty(first, 3L)
    assertEquals(listOf(OutboxEntry(first, 3L), OutboxEntry(second, 9L)), database.getOutbox())
  }

  @Test
  fun outboxAcceptsAllFiveKeyKinds() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val node = DirtyKey.NodeKey(key0)
    val edge = DirtyKey.EdgeKey(key0, key1)
    val setting = DirtyKey.SettingKey("appTheme")
    val repertoire = DirtyKey.RepertoireKey("italian-game")
    val tag = DirtyKey.TagKey(key0, key1, "italian-game")
    database.markDirty(node, 1L)
    database.markDirty(edge, 1L)
    database.markDirty(setting, 1L)
    database.markDirty(repertoire, 1L)
    database.markDirty(tag, 1L)
    assertEquals(
      setOf(node, edge, setting, repertoire, tag),
      database.getOutbox().map { it.key }.toSet(),
    )
  }

  @Test
  fun insertRepertoireQueuesItsOwnRepertoireKey() = runTest {
    val database = InMemoryDatabaseQueryManager()

    database.insertRepertoire(
      DataRepertoire(id = "italian-game", name = "Italian Game", color = null)
    )

    assertEquals(
      setOf(DirtyKey.RepertoireKey("italian-game")),
      database.getOutbox().map { it.key }.toSet(),
    )
  }

  @Test
  fun insertTagQueuesItsOwnTagKey() = runTest {
    val database = InMemoryDatabaseQueryManager()

    database.insertTag(
      DataEdgeRepertoireTag(key0, key1, repertoireId = "italian-game", move = "e4")
    )

    assertEquals(
      setOf(DirtyKey.TagKey(key0, key1, "italian-game")),
      database.getOutbox().map { it.key }.toSet(),
    )
  }

  @Test
  fun softDeletingAPositionCascadesTheTombstoneToIncidentMovesOnBothEnds() = runTest {
    val database = seededLine()
    val stamp = Instant.fromEpochSeconds(1_234)
    database.deletePosition(key1, "device-a", 5L, stamp)
    assertTrue(database.getPosition(key0)!!.previousAndNextMoves.nextMoves.getValue("e4").isDeleted)
    assertTrue(
      database.getPosition(key2)!!.previousAndNextMoves.previousMoves.getValue("e5").isDeleted
    )
  }

  @Test
  fun deletingMoveThroughTreeStorePersistDoesNotClobberTheTombstoneWrittenByDeleteMove() = runTest {
    val database = seededLine()
    database.deleteMove(key1, "e5", "device-a", 1L, Instant.fromEpochSeconds(1))
    // TreeStore then persists the origin from a cache that no longer has the edge.
    database.insertNodes(node(key1, previous = listOf(moveE4), next = listOf()))
    val tombstone = database.getPosition(key1)!!.previousAndNextMoves.nextMoves.getValue("e5")
    assertTrue(tombstone.isDeleted)
  }

  @Test
  fun reAddingASoftDeletedMoveRevivesItAsLive() = runTest {
    val database = seededLine()
    database.deleteMove(key1, "e5", "device-a", 1L, Instant.fromEpochSeconds(1))

    database.insertNodes(
      node(key1, previous = listOf(moveE4), next = listOf(moveE5)),
      node(key2, previous = listOf(moveE5), next = listOf()),
    )

    assertTrue(
      !database.getPosition(key1)!!.previousAndNextMoves.nextMoves.getValue("e5").isDeleted
    )
  }

  @Test
  fun insertRepertoireThenGetRepertoireReturnsIt() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val repertoire = DataRepertoire(id = "italian-game", name = "Italian Game", color = null)

    database.insertRepertoire(repertoire)

    assertEquals(repertoire, database.getRepertoire("italian-game"))
  }

  @Test
  fun insertTagThenGetTagsReturnsTheLiveTagDeletedTagsExcluded() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val origin = key0
    val destination = key1
    val live =
      DataEdgeRepertoireTag(origin, destination, repertoireId = "italian-game", move = "e4")
    val deleted =
      DataEdgeRepertoireTag(
        origin,
        destination,
        repertoireId = "ruy-lopez",
        move = "e4",
        isDeleted = true,
      )

    database.insertTag(live)
    database.insertTag(deleted)

    assertEquals(listOf(live), database.getTags(origin, destination))
  }

  @Test
  fun edgesTaggedWithJoinsTheTagOntoItsMoveForItsSan() = runTest {
    val database = seededLine()
    database.insertTag(
      DataEdgeRepertoireTag(key0, key1, repertoireId = "italian-game", move = "e4")
    )

    val edges = database.edgesTaggedWith("italian-game")

    assertEquals(listOf(TaggedEdge(key0, key1, "e4")), edges)
  }

  @Test
  fun edgesTaggedWithExcludesADeletedTagOrADeletedMove() = runTest {
    val database = seededLine()
    database.insertTag(
      DataEdgeRepertoireTag(key0, key1, repertoireId = "deleted-tag", move = "e4", isDeleted = true)
    )
    database.deleteMove(key1, "e5", "device-a", 1L, Instant.fromEpochSeconds(1))
    database.insertTag(
      DataEdgeRepertoireTag(key1, key2, repertoireId = "deleted-move", move = "e4")
    )

    assertEquals(emptyList(), database.edgesTaggedWith("deleted-tag"))
    assertEquals(emptyList(), database.edgesTaggedWith("deleted-move"))
  }

  @Test
  fun edgesTaggedWithReturnsEmptyForAnUnknownRepertoire() = runTest {
    val database = seededLine()

    assertEquals(emptyList(), database.edgesTaggedWith("never-tagged"))
  }

  @Test
  fun edgesTaggedWithExcludesATagWhoseOriginNodeDoesNotExist() = runTest {
    val database = InMemoryDatabaseQueryManager()
    database.insertTag(
      DataEdgeRepertoireTag(key0, key1, repertoireId = "italian-game", move = "e4")
    )

    assertEquals(emptyList(), database.edgesTaggedWith("italian-game"))
  }

  @Test
  fun replaceTrainableRepertoiresOverwritesThePreviousMembershipSet() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val position = key0
    // The node must exist, since only trainable rows of live positions count.
    database.insertNodes(DataNode(position, PreviousAndNextMoves(), CardStateFactory.new()))

    database.replaceTrainableRepertoires(
      position,
      setOf("italian-game", "ruy-lopez"),
      lastReview = null,
    )
    database.replaceTrainableRepertoires(position, setOf("ruy-lopez"), lastReview = null)
    val snapshots = database.getRepertoireMasterySnapshots(listOf("italian-game", "ruy-lopez"))

    assertEquals(RepertoireMasterySnapshot(0, 0, null), snapshots.getValue("italian-game"))
    assertEquals(RepertoireMasterySnapshot(0, 1, null), snapshots.getValue("ruy-lopez"))
  }

  @Test
  fun getRepertoireMasterySnapshotsCountsSolidPositionsAndTracksTheLatestReview() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val solidPosition = key0
    val newPosition = key1
    val reviewedAt = Instant.fromEpochSeconds(2_000)
    database.insertNodes(
      DataNode(
        solidPosition,
        PreviousAndNextMoves(),
        CardStateFactory.new().copy(phase = CardPhase.REVIEW, lastReview = reviewedAt),
      ),
      DataNode(newPosition, PreviousAndNextMoves(), CardStateFactory.new()),
    )
    database.replaceTrainableRepertoires(solidPosition, setOf("italian-game"), reviewedAt)
    database.replaceTrainableRepertoires(newPosition, setOf("italian-game"), lastReview = null)

    val snapshot =
      database.getRepertoireMasterySnapshots(listOf("italian-game")).getValue("italian-game")

    assertEquals(
      RepertoireMasterySnapshot(solidCount = 1, totalCount = 2, lastReview = reviewedAt),
      snapshot,
    )
  }

  @Test
  fun getRepertoireMasterySnapshotsReturnsAZeroSnapshotForARepertoireWithNoTrainablePosition() =
    runTest {
      val database = InMemoryDatabaseQueryManager()

      val snapshots = database.getRepertoireMasterySnapshots(listOf("empty-repertoire"))

      assertEquals(RepertoireMasterySnapshot(0, 0, null), snapshots.getValue("empty-repertoire"))
    }

  @Test
  fun getRepertoireMasterySnapshotsExcludesATrainableRowWhoseOwnPositionWasDeleted() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val position = key0
    val reviewedAt = Instant.fromEpochSeconds(2_000)
    database.insertNodes(
      DataNode(
        position,
        PreviousAndNextMoves(),
        CardStateFactory.new().copy(phase = CardPhase.REVIEW, lastReview = reviewedAt),
      )
    )
    database.replaceTrainableRepertoires(position, setOf("italian-game"), reviewedAt)

    database.deletePosition(position, "device-a", 1L, DateUtil.now())

    val snapshot =
      database.getRepertoireMasterySnapshots(listOf("italian-game")).getValue("italian-game")
    assertEquals(RepertoireMasterySnapshot(0, 0, null), snapshot)
  }

  @Test
  fun nextDueNewCardExcludesACardOutsideTheGivenRepertoire() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val taggedPosition = PositionKey("posA b K")
    database.insertNodes(
      schedNode("posA b K", CardPhase.NEW, dueDate = Instant.fromEpochSeconds(100)),
      schedNode("posB w K", CardPhase.NEW, dueDate = Instant.fromEpochSeconds(100)),
    )
    database.replaceTrainableRepertoires(taggedPosition, setOf("italian-game"), null)

    val result = database.nextDueNewCard(dayEnd, repertoireId = "italian-game")

    assertEquals(taggedPosition, result?.positionKey)
  }

  @Test
  fun nextDueNewCardWithANullRepertoireIdReproducesTheUnscopedResult() = runTest {
    val database = InMemoryDatabaseQueryManager()
    database.insertNodes(
      schedNode("posA b K", CardPhase.NEW, dueDate = Instant.fromEpochSeconds(100))
    )

    assertEquals(
      database.nextDueNewCard(dayEnd),
      database.nextDueNewCard(dayEnd, repertoireId = null),
    )
  }

  @Test
  fun getScopedCountsReportsOnlyTheGivenRepertoiresDueAndInSessionCards() = runTest {
    val database = InMemoryDatabaseQueryManager()
    val taggedPosition = PositionKey("posA b K")
    database.insertNodes(
      schedNode("posA b K", CardPhase.NEW, dueDate = Instant.fromEpochSeconds(100)),
      schedNode("posB w K", CardPhase.NEW, dueDate = Instant.fromEpochSeconds(100)),
    )
    database.replaceTrainableRepertoires(taggedPosition, setOf("italian-game"), null)

    val counts = database.getScopedCounts(dayEnd, "italian-game")

    assertEquals(ScopedSchedulingCounts(dueReviews = 0, dueNew = 1, inSession = 0), counts)
  }
}
