package proj.memorchess.axl.core.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.test_util.TestDatabaseQueryManager

/** Behavioural tests for the in memory graph, driven through [TreeStore]. */
class TestOpeningTree {

  private val startPos = PositionKey("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq")
  private val posA = PositionKey("posA b K")
  private val posB = PositionKey("posB w K")
  private val posC = PositionKey("posC b K")
  private val posD = PositionKey("posD b K")

  @Test
  fun computeStateWithEmptyIncoming() = runTest {
    val store = TreeStore(TestDatabaseQueryManager.empty())
    store.ensurePosition(startPos, 0)
    assertEquals(NodeState.FIRST, store.current().computeState(startPos, null))
  }

  @Test
  fun computeStateUnknownPosition() {
    val tree = OpeningTree()
    assertEquals(NodeState.UNKNOWN, tree.computeState(startPos, null))
  }

  @Test
  fun computeStateSavedGood() = runTest {
    val store = TreeStore(TestDatabaseQueryManager.empty())
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    assertEquals(NodeState.SAVED_GOOD, store.current().computeState(posA, startPos))
  }

  @Test
  fun computeStateSavedBad() = runTest {
    val store = TreeStore(TestDatabaseQueryManager.empty())
    store.addMove(from = startPos, move = "e4", to = posA, isGood = false, fromDepth = 0)
    assertEquals(NodeState.SAVED_BAD, store.current().computeState(posA, startPos))
  }

  @Test
  fun computeStateMixedGoodBadReturnsBadState() = runTest {
    val store = TreeStore(TestDatabaseQueryManager.empty())
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = posB, move = "d4", to = posA, isGood = false, fromDepth = 0)
    assertEquals(NodeState.BAD_STATE, store.current().computeState(posA, startPos))
  }

  @Test
  fun computeStateSavedGoodButUnknownMove() = runTest {
    val store = TreeStore(TestDatabaseQueryManager.empty())
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    assertEquals(NodeState.SAVED_GOOD_BUT_UNKNOWN_MOVE, store.current().computeState(posA, posB))
  }

  @Test
  fun countDescendantsLinearChain() = runTest {
    val store = TreeStore(TestDatabaseQueryManager.empty())
    store.addMove(from = posA, move = "e4", to = posB, isGood = true, fromDepth = 0)
    store.addMove(from = posB, move = "e5", to = posC, isGood = true, fromDepth = 1)
    assertEquals(3, store.current().countDescendants(posA))
  }

  @Test
  fun countDescendantsStopsAtConvergence() = runTest {
    val store = TreeStore(TestDatabaseQueryManager.empty())
    store.addMove(from = posA, move = "e4", to = posB, isGood = true, fromDepth = 0)
    store.addMove(from = posB, move = "e5", to = posC, isGood = true, fromDepth = 1)
    store.addMove(from = posD, move = "d4", to = posC, isGood = true, fromDepth = 0)
    assertEquals(1, store.current().countDescendants(posB, "e4"))
  }

  @Test
  fun ensurePositionUpdatesDepthToMinimum() = runTest {
    val store = TreeStore(TestDatabaseQueryManager.empty())
    store.ensurePosition(posA, 5)
    assertEquals(5, store.current().getDepth(posA))
    store.ensurePosition(posA, 2)
    assertEquals(2, store.current().getDepth(posA))
    store.ensurePosition(posA, 10)
    assertEquals(2, store.current().getDepth(posA))
  }

  @Test
  fun eraseAllRemovesEverything() = runTest {
    val store = TreeStore(TestDatabaseQueryManager.empty())
    store.addMove(from = posA, move = "e4", to = posB, isGood = true, fromDepth = 0)
    store.eraseAll()
    assertNull(store.current().get(posA))
    assertNull(store.current().get(posB))
  }

  @Test
  fun updateCardStateOnUnknownPositionIsANoOp() = runTest {
    val store = TreeStore(TestDatabaseQueryManager.empty())
    // posA is not in the tree. The call should log a warning and return without throwing.
    store.updateCardState(posA, CardStateFactory.new())
    assertNull(store.current().get(posA))
  }
}
