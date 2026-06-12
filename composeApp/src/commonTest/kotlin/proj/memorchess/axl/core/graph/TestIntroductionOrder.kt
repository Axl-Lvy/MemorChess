package proj.memorchess.axl.core.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.test_util.TestDatabaseQueryManager

/**
 * Tests for [OpeningTree.introductionOrder]: new positions are numbered depth first along a line,
 * oldest branch first, so a whole line is introduced before the next branch starts.
 */
class TestIntroductionOrder {

  private val start = PositionKey.START_POSITION
  private val posA = PositionKey("posA b K")
  private val posB = PositionKey("posB w K")
  private val posC = PositionKey("posC b K")
  private val posD = PositionKey("posD w K")

  private val t1 = Instant.parse("2026-01-01T00:00:00Z")
  private val t2 = Instant.parse("2026-01-02T00:00:00Z")

  private fun move(from: PositionKey, san: String, to: PositionKey, createdAt: Instant) =
    DataMove(
      origin = from,
      destination = to,
      move = san,
      isGood = true,
      createdAt = createdAt,
      updatedAt = createdAt,
    )

  private fun node(
    key: PositionKey,
    depth: Int,
    incoming: List<DataMove>,
    outgoing: List<DataMove>,
  ) =
    DataNode(
      positionKey = key,
      previousAndNextMoves = PreviousAndNextMoves(incoming, outgoing),
      cardState = CardStateFactory.new(),
      depth = depth,
    )

  private suspend fun loadGraph(vararg nodes: DataNode): OpeningTree {
    val database = TestDatabaseQueryManager.empty()
    database.insertNodes(*nodes)
    val store = TreeStore(database)
    store.load()
    return store.current()
  }

  @Test
  fun introductionOrderFollowsLineDepthFirst() = runTest {
    val e4 = move(start, "e4", posA, t1)
    val e5 = move(posA, "e5", posB, t1)
    val tree =
      loadGraph(
        node(start, 0, emptyList(), listOf(e4)),
        node(posA, 1, listOf(e4), listOf(e5)),
        node(posB, 2, listOf(e5), emptyList()),
      )
    val order = tree.introductionOrder()
    assertEquals(0, order[start])
    assertEquals(1, order[posA])
    assertEquals(2, order[posB])
  }

  @Test
  fun introductionOrderServesFirstBranchFullyBeforeSecond() = runTest {
    // Older branch: e4 then e5. Newer branch: d4 then d5. The whole e4 line must be numbered
    // before the d4 line even though posC sits at the same depth as posA.
    val e4 = move(start, "e4", posA, t1)
    val e5 = move(posA, "e5", posB, t1)
    val d4 = move(start, "d4", posC, t2)
    val d5 = move(posC, "d5", posD, t2)
    val tree =
      loadGraph(
        node(start, 0, emptyList(), listOf(e4, d4)),
        node(posA, 1, listOf(e4), listOf(e5)),
        node(posB, 2, listOf(e5), emptyList()),
        node(posC, 1, listOf(d4), listOf(d5)),
        node(posD, 2, listOf(d5), emptyList()),
      )
    val order = tree.introductionOrder()
    assertEquals(0, order[start])
    assertEquals(1, order[posA])
    assertEquals(2, order[posB])
    assertEquals(3, order[posC])
    assertEquals(4, order[posD])
  }

  @Test
  fun introductionOrderBreaksEqualCreatedAtBySanAlphabetically() = runTest {
    val e4 = move(start, "e4", posA, t1)
    val d4 = move(start, "d4", posC, t1)
    val tree =
      loadGraph(
        node(start, 0, emptyList(), listOf(e4, d4)),
        node(posA, 1, listOf(e4), emptyList()),
        node(posC, 1, listOf(d4), emptyList()),
      )
    val order = tree.introductionOrder()
    assertEquals(1, order[posC], "d4 sorts before e4 when both branches were created together")
    assertEquals(2, order[posA])
  }

  @Test
  fun introductionOrderIndexesTranspositionAtFirstVisit() = runTest {
    // posB is reachable from both branches. It must get exactly one index, assigned while the
    // older branch is being walked.
    val e4 = move(start, "e4", posA, t1)
    val viaA = move(posA, "c4", posB, t1)
    val d4 = move(start, "d4", posC, t2)
    val viaC = move(posC, "e4", posB, t2)
    val tree =
      loadGraph(
        node(start, 0, emptyList(), listOf(e4, d4)),
        node(posA, 1, listOf(e4), listOf(viaA)),
        node(posC, 1, listOf(d4), listOf(viaC)),
        node(posB, 2, listOf(viaA, viaC), emptyList()),
      )
    val order = tree.introductionOrder()
    assertEquals(4, order.size)
    assertEquals(2, order[posB], "transposition is numbered inside the branch visited first")
    assertEquals(3, order[posC])
  }

  @Test
  fun introductionOrderAppendsUnreachableNodesByDepthThenKey() = runTest {
    val e4 = move(start, "e4", posA, t1)
    val tree =
      loadGraph(
        node(start, 0, emptyList(), listOf(e4)),
        node(posA, 1, listOf(e4), emptyList()),
        // Orphans, not connected to the start position. posD is deeper than posC.
        node(posD, 5, emptyList(), emptyList()),
        node(posC, 3, emptyList(), emptyList()),
      )
    val order = tree.introductionOrder()
    assertEquals(2, order[posC])
    assertEquals(3, order[posD])
  }

  @Test
  fun introductionOrderWithoutStartPositionFallsBackToDepthOrder() = runTest {
    val tree =
      loadGraph(node(posB, 2, emptyList(), emptyList()), node(posA, 1, emptyList(), emptyList()))
    val order = tree.introductionOrder()
    assertEquals(0, order[posA])
    assertEquals(1, order[posB])
  }

  @Test
  fun introductionOrderIgnoresDeletedEdges() = runTest {
    val e4 = move(start, "e4", posA, t1).copy(isDeleted = true)
    val d4 = move(start, "d4", posC, t2)
    val database = TestDatabaseQueryManager.empty()
    database.insertNodes(
      node(start, 0, emptyList(), listOf(e4, d4)),
      node(posA, 1, listOf(e4), emptyList()),
      node(posC, 1, listOf(d4), emptyList()),
    )
    val store = TreeStore(database)
    store.load()
    val order = store.current().introductionOrder()
    assertEquals(1, order[posC], "the deleted older branch must not be followed")
    assertTrue(order.getValue(posA) > order.getValue(posC))
  }

  @Test
  fun addMovePreservesCreatedAtOnReupsert() = runTest {
    val store = TreeStore(TestDatabaseQueryManager.empty())
    store.addMove(from = start, move = "e4", to = posA, isGood = null, fromDepth = 0)
    val created = store.current().get(start)!!.outgoing.getValue("e4").createdAt
    store.addMove(from = start, move = "e4", to = posA, isGood = true, fromDepth = 0)
    val afterReupsert = store.current().get(start)!!.outgoing.getValue("e4").createdAt
    assertEquals(created, afterReupsert)
  }
}
