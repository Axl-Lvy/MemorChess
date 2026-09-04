package proj.memorchess.axl.core.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.test_util.TestDatabases
import proj.memorchess.axl.test_util.testTreeStore

/** Behavioural tests for the demand paged graph, driven through [TreeStore]. */
class TestOpeningTree {

  private val startPos = PositionKey("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq")
  private val posA = PositionKey("posA b K")
  private val posB = PositionKey("posB w K")
  private val posC = PositionKey("posC b K")

  @Test
  fun computeStateWithEmptyIncoming() = runTest {
    val store = testTreeStore(TestDatabases.empty())
    store.ensurePosition(startPos, 0)
    assertEquals(NodeState.FIRST, store.computeState(startPos, null))
  }

  @Test
  fun computeStateUnknownPosition() = runTest {
    val store = testTreeStore(TestDatabases.empty())
    assertEquals(NodeState.UNKNOWN, store.computeState(startPos, null))
  }

  @Test
  fun computeStateSavedGood() = runTest {
    val store = testTreeStore(TestDatabases.empty())
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    assertEquals(NodeState.SAVED_GOOD, store.computeState(posA, startPos))
  }

  @Test
  fun computeStateSavedBad() = runTest {
    val store = testTreeStore(TestDatabases.empty())
    store.addMove(from = startPos, move = "e4", to = posA, isGood = false, fromDepth = 0)
    assertEquals(NodeState.SAVED_BAD, store.computeState(posA, startPos))
  }

  @Test
  fun computeStateMixedGoodBadReturnsBadState() = runTest {
    val store = testTreeStore(TestDatabases.empty())
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    store.addMove(from = posB, move = "d4", to = posA, isGood = false, fromDepth = 0)
    assertEquals(NodeState.BAD_STATE, store.computeState(posA, startPos))
  }

  @Test
  fun computeStateSavedGoodButUnknownMove() = runTest {
    val store = testTreeStore(TestDatabases.empty())
    store.addMove(from = startPos, move = "e4", to = posA, isGood = true, fromDepth = 0)
    assertEquals(NodeState.SAVED_GOOD_BUT_UNKNOWN_MOVE, store.computeState(posA, posB))
  }

  @Test
  fun ensurePositionUpdatesDepthToMinimum() = runTest {
    val store = testTreeStore(TestDatabases.empty())
    store.ensurePosition(posA, 5)
    assertEquals(5, store.getDepth(posA))
    store.ensurePosition(posA, 2)
    assertEquals(2, store.getDepth(posA))
    store.ensurePosition(posA, 10)
    assertEquals(2, store.getDepth(posA))
  }

  @Test
  fun eraseAllRemovesEverything() = runTest {
    val store = testTreeStore(TestDatabases.empty())
    store.addMove(from = posA, move = "e4", to = posB, isGood = true, fromDepth = 0)
    store.eraseAll()
    assertNull(store.node(posA))
    assertNull(store.node(posB))
  }

  @Test
  fun updateCardStateOnUnknownPositionIsANoOp() = runTest {
    val store = testTreeStore(TestDatabases.empty())
    // posA is not in the graph. The call should log a warning and return without throwing.
    store.updateCardState(posA, CardStateFactory.new())
    assertNull(store.node(posA))
  }

  @Test
  fun edgeSyncMetadataIsIndependentOfEndpoints() {
    val now = proj.memorchess.axl.core.date.DateUtil.now()
    val edge =
      Edge(
        from = startPos,
        move = "e4",
        to = posA,
        isGood = true,
        createdAt = now,
        updatedAt = now,
        originDevice = "device-a",
        deviceSeq = 1L,
      )
    val restamped = edge.copy(originDevice = "device-b", deviceSeq = 99L)

    assertEquals(edge.from, restamped.from)
    assertEquals(edge.to, restamped.to)
    assertEquals("device-a", edge.originDevice)
    assertEquals("device-b", restamped.originDevice)
  }
}
