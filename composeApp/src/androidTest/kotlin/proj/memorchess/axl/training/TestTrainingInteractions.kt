package proj.memorchess.axl.training

import androidx.compose.ui.test.performClick
import kotlin.test.BeforeTest
import kotlin.test.Test
import proj.memorchess.axl.core.data.DatabaseHolder
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.INextDateCalculator
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.utils.AUiTestFromMainActivity
import proj.memorchess.axl.utils.Awaitility

private const val BRAVO_TEXT = "Bravo !"

class TestTrainingInteractions : AUiTestFromMainActivity() {

  @BeforeTest
  override fun setUp() {
    super.setUp()
    goToTraining()
  }

  override suspend fun resetDatabase() {
    // Delete all existing nodes
    DatabaseHolder.getDatabase().deleteAllNodes()
    DatabaseHolder.getDatabase().deleteAllMoves()

    // Create a test node with e4 as a good move
    val game = Game()
    val startPos = game.position.toImmutablePosition()

    game.playMove("e4")
    val e4Pos = game.position.toImmutablePosition()
    val e4Move = StoredMove(startPos, e4Pos, "e4", isGood = true)

    // Create the node with the move
    val testNode =
      StoredNode(
        positionKey = startPos,
        PreviousAndNextMoves(listOf(), listOf(e4Move)),
        PreviousAndNextDate(DateUtil.dateInDays(-7), DateUtil.today()),
      )

    // Save the node to the database
    DatabaseHolder.getDatabase().insertPosition(testNode)
  }

  @Test
  fun testSucceedOfTraining() {
    assertNodeWithTextDoesNotExists(BRAVO_TEXT)
    playMove("e2", "e4")
    assertNodeWithTextExists(BRAVO_TEXT)
    Awaitility.awaitUntilTrue(TEST_TIMEOUT, "testSucceedOfTraining: Position not saved") {
      val positions = getAllPositions()
      positions.size == 1 &&
        positions[0].positionKey == PositionKey.START_POSITION &&
        positions[0].previousAndNextTrainingDate.previousDate.dayOfYear ==
          DateUtil.today().dayOfYear &&
        positions[0].previousAndNextTrainingDate.nextDate.dayOfYear ==
          INextDateCalculator.SUCCESS.calculateNextDate(
              PreviousAndNextDate(DateUtil.dateInDays(-7), DateUtil.today())
            )
            .dayOfYear
    }
  }

  @Test
  fun testFailTraining() {
    assertNodeWithTextDoesNotExists(BRAVO_TEXT)
    playMove("e2", "e3")
    assertNodeWithTextExists(BRAVO_TEXT)
    Awaitility.awaitUntilTrue(TEST_TIMEOUT, "testSaveBad: Position not saved") {
      val positions = getAllPositions()
      positions.size == 1 &&
        positions[0].positionKey == PositionKey.START_POSITION &&
        positions[0].previousAndNextTrainingDate.previousDate.dayOfYear ==
          DateUtil.today().dayOfYear &&
        positions[0].previousAndNextTrainingDate.nextDate.dayOfYear ==
          INextDateCalculator.FAILURE.calculateNextDate(
              PreviousAndNextDate(DateUtil.dateInDays(-7), DateUtil.today())
            )
            .dayOfYear
    }
  }

  @Test
  fun testIncrementDay() {
    assertNodeWithTextDoesNotExists(BRAVO_TEXT)
    playMove("e2", "e3")
    assertNodeWithTextExists(BRAVO_TEXT)
    assertNodeWithTextExists("Increment a day").performClick()
    assertNodeWithTextDoesNotExists(BRAVO_TEXT)
    playMove("e2", "e4")
    assertNodeWithTextExists("Days in advance: 1")
  }
}
