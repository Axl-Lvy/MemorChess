package proj.memorchess.axl.core.graph

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.test_util.TestDatabases

/**
 * Tests for the derived projection column [DataNode.hasGoodOutgoing], maintained by [TreeStore] on
 * every write. Assertions read the persisted [DataNode] back through the public
 * [proj.memorchess.axl.core.data.DatabaseQueryManager.getPosition] API.
 */
class TestTreeStore {

  private val start = PositionKey.START_POSITION
  private val posA = PositionKey("posA b K")

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
}
