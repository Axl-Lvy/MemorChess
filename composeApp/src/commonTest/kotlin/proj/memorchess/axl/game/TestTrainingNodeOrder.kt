package proj.memorchess.axl.game

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.DatabaseHolder
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.test_util.TestDatabase

class TestTrainingNodeOrder {
  @BeforeTest
  fun setup() {
    DatabaseHolder.init(TestDatabase.vienna())
    runTest { NodeManager.resetCacheFromDataBase() }
  }

  @Test
  fun testMinimumDepth() {
    val node = NodeManager.getNextNodeToLearn(0, null)
    assertNotNull(node)
    assertTrue { node.positionKey == PositionKey.START_POSITION }
  }

  @Test
  fun testNextMove() {
    val node = NodeManager.getNextNodeToLearn(0, null)
    checkNotNull(node)
    val nextNode =
      NodeManager.getNextNodeToLearn(0, node.previousAndNextMoves.nextMoves.iterator().next().value)
    assertNotNull(nextNode)
    val move = nextNode.previousAndNextMoves.nextMoves.iterator().next().value.move
    assertTrue("Move is $move but should be Nc3") { move == "Nc3" }
  }
}
