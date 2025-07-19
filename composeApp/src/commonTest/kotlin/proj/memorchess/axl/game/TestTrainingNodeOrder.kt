package proj.memorchess.axl.game

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.LocalDatabaseHolder
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.test_util.TestDatabaseQueryManager

class TestTrainingNodeOrder {
  @BeforeTest
  fun setup() {
    LocalDatabaseHolder.init(TestDatabaseQueryManager.vienna())
    runTest { NodeManager.resetCacheFromDataBase() }
  }

  @AfterTest
  fun tearDown() {
    LocalDatabaseHolder.reset()
  }

  @Test
  fun testMinimumDepth() {
    val node = NodeManager.getNextNodeToLearn(0, null)
    assertNotNull(node)
    assertTrue { node.positionIdentifier == PositionIdentifier.START_POSITION }
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
