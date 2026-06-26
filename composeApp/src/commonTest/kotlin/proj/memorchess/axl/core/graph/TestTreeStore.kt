package proj.memorchess.axl.core.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.test_util.TestDatabases

/**
 * Tests for the derived projection columns [DataNode.hasGoodOutgoing] and [DataNode.createdAt],
 * maintained by [TreeStore] on every write. Assertions read the persisted [DataNode] back through
 * the public [proj.memorchess.axl.core.data.DatabaseQueryManager.getPosition] API.
 */
class TestTreeStore {

  private val start = PositionKey.START_POSITION
  private val posA = PositionKey("posA b K")
  private val posB = PositionKey("posB w K")
  private val posC = PositionKey("posC b K")

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

  @Test
  fun classifyingFirstGoodEdgeSetsHasGoodOutgoingTrue() = runTest {
    val database = TestDatabases.empty()
    val store = TreeStore(database)
    store.addMove(from = start, move = "e4", to = posA, isGood = true, fromDepth = 0)
    val persisted = database.getPosition(start)
    assertNotNull(persisted)
    assertTrue(persisted.hasGoodOutgoing, "a classified good edge must set hasGoodOutgoing")
  }

  @Test
  fun deletingLastGoodEdgeSetsHasGoodOutgoingFalse() = runTest {
    val database = TestDatabases.empty()
    val store = TreeStore(database)
    store.addMove(from = start, move = "e4", to = posA, isGood = true, fromDepth = 0)
    assertTrue(database.getPosition(start)!!.hasGoodOutgoing)
    store.deleteMove(from = start, move = "e4")
    val afterDelete = database.getPosition(start)
    assertNotNull(afterDelete)
    assertFalse(
      afterDelete.hasGoodOutgoing,
      "deleting the last good edge must flip hasGoodOutgoing back to false",
    )
  }

  @Test
  fun deletingNodeRePersistsSurvivingOrigin() = runTest {
    val database = TestDatabases.empty()
    val store = TreeStore(database)
    store.addMove(from = start, move = "e4", to = posA, isGood = true, fromDepth = 0)
    assertTrue(database.getPosition(start)!!.hasGoodOutgoing)
    store.deleteNode(posA)
    val origin = database.getPosition(start)
    assertNotNull(origin)
    assertFalse(
      origin.hasGoodOutgoing,
      "deleting the only good destination must flip the origin's hasGoodOutgoing to false",
    )
  }

  @Test
  fun explorationEdgeDoesNotSetHasGoodOutgoing() = runTest {
    val database = TestDatabases.empty()
    val store = TreeStore(database)
    // An exploration edge (isGood == null) is not persisted, then classify it as a known mistake.
    store.addMove(from = start, move = "e4", to = posA, isGood = false, fromDepth = 0)
    val persisted = database.getPosition(start)
    assertNotNull(persisted)
    assertFalse(
      persisted.hasGoodOutgoing,
      "a bad (isGood == false) edge must not set hasGoodOutgoing",
    )
  }

  @Test
  fun nodeCreatedAtIsEarliestIncomingEdgeCreatedAt() = runTest {
    // posB has two good incoming edges with distinct createdAt; the persisted createdAt must be the
    // earlier one. Seed via insertNodes + load so the edge createdAt values are controlled, then a
    // write to posB re-persists it through Node.toDataNode().
    val viaA = move(posA, "Nc3", posB, t2)
    val viaC = move(posC, "Nf3", posB, t1)
    val database = TestDatabases.empty()
    database.insertNodes(
      node(posA, 1, emptyList(), listOf(viaA)),
      node(posC, 1, emptyList(), listOf(viaC)),
      node(posB, 2, listOf(viaA, viaC), emptyList()),
    )
    val store = TreeStore(database)
    store.load()
    // Trigger a re-persist of posB by adding an outgoing edge from it.
    store.addMove(from = posB, move = "d4", to = start, isGood = true, fromDepth = 2)
    val persisted = database.getPosition(posB)
    assertNotNull(persisted)
    assertEquals(
      t1,
      persisted.createdAt,
      "createdAt must be the earliest non deleted incoming edge createdAt",
    )
  }

  @Test
  fun rootNodeCreatedAtFallsBackToNow() = runTest {
    val database = TestDatabases.empty()
    val store = TreeStore(database)
    // The start position is persisted as the origin of a classified edge; it has no incoming edges,
    // so createdAt falls back to now and is a set, non throwing value within the call window.
    val before = DateUtil.now()
    store.addMove(from = start, move = "e4", to = posA, isGood = true, fromDepth = 0)
    val after = DateUtil.now()
    val persisted = database.getPosition(start)
    assertNotNull(persisted)
    assertTrue(
      persisted.createdAt >= before && persisted.createdAt <= after,
      "root createdAt must fall back to ~now when there are no incoming edges",
    )
  }
}
