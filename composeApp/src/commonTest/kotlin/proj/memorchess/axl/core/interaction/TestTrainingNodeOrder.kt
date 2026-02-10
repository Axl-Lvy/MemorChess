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
import proj.memorchess.axl.core.graph.nodes.PersonalNode
import proj.memorchess.axl.test_util.TestDatabaseQueryManager
import proj.memorchess.axl.test_util.TestWithKoin

class TestTrainingNodeOrder : TestWithKoin {

  private val nodeManager: NodeManager<PersonalNode> by inject()
  private val database: DatabaseQueryManager by inject()

  @BeforeTest
  override fun setUp() {
    super.setUp()
    runTest {
      database.deleteAll(DateUtil.farInThePast())
      val vienna = TestDatabaseQueryManager.vienna()
      database.insertMoves(vienna.dataMoves, vienna.dataPositions.values.toList())
      nodeManager.resetCacheFromSource()
    }
  }

  @Test
  fun testMinimumDepth() {
    val position = nodeManager.getNextPositionToLearn(0, null)
    assertNotNull(position)
    assertTrue { position.positionIdentifier == PositionIdentifier.START_POSITION }
  }

  @Test
  fun testNextMove() {
    val position = nodeManager.getNextPositionToLearn(0, null)
    checkNotNull(position)
    val movesForPosition = checkNotNull(nodeManager.getMovesForPosition(position.positionIdentifier))
    val nextMove = movesForPosition.nextMoves.iterator().next().value
    val nextPosition = nodeManager.getNextPositionToLearn(0, nextMove)
    assertNotNull(nextPosition)
    val nextPositionMoves = checkNotNull(nodeManager.getMovesForPosition(nextPosition.positionIdentifier))
    val move = nextPositionMoves.nextMoves.iterator().next().value.move
    assertTrue("Move is $move but should be Nc3") { move == "Nc3" }
  }
}
