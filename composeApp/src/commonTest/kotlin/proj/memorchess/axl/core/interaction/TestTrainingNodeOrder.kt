package proj.memorchess.axl.core.interaction

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.test_util.TestDatabaseQueryManager
import proj.memorchess.axl.test_util.TestWithKoin

class TestTrainingNodeOrder : TestWithKoin {

  private val nodeManager: NodeManager by inject()
  private val database: DatabaseQueryManager by inject()

  @BeforeTest
  override fun setUp() {
    super.setUp()
    runTest {
      database.deleteAll(DateUtil.farInThePast())
      database.insertNodes(*TestDatabaseQueryManager.vienna().getAllNodes(true).toTypedArray())
      nodeManager.resetCacheFromDataBase()
    }
  }

  @Test
  fun testMinimumDepth() {
    val node = nodeManager.getNextNodeToLearn(0, null)
    assertNotNull(node)
    assertTrue { node.positionIdentifier == PositionIdentifier.START_POSITION }
  }

  @Test
  fun testNextMove() {
    val node = nodeManager.getNextNodeToLearn(0, null)
    checkNotNull(node)
    val nextNode =
      nodeManager.getNextNodeToLearn(0, node.previousAndNextMoves.nextMoves.iterator().next().value)
    assertNotNull(nextNode)
    val move = nextNode.previousAndNextMoves.nextMoves.iterator().next().value.move
    assertTrue("Move is $move but should be Nc3") { move == "Nc3" }
  }
}
