package proj.memorchess.axl.training

import androidx.compose.ui.test.performClick
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.NextDateCalculator
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.Pawn
import proj.memorchess.axl.core.engine.pieces.Piece
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.utils.Awaitility
import proj.memorchess.axl.utils.TestFromMainActivity

private const val BRAVO_TEXT = "Bravo !"

class TestTraining : TestFromMainActivity() {

  @BeforeTest
  override fun setUp() {
    super.setUp()
    goToTraining()
  }

  override suspend fun resetDatabase() {
    // Delete all existing nodes
    database.deleteAll(null)

    // Create a test node with e4 as a good move
    val game = Game()
    val startPos = game.position.createIdentifier()

    game.playMove("e4")
    val e4Pos = game.position.createIdentifier()
    val e4Move = StoredMove(startPos, e4Pos, "e4", isGood = true)

    // Create the node with the move
    val testNode =
      StoredNode(
        positionIdentifier = startPos,
        PreviousAndNextMoves(listOf(), listOf(e4Move)),
        PreviousAndNextDate(DateUtil.dateInDays(-7), DateUtil.today()),
      )

    // Save the node to the database
    database.insertNodes(testNode)
  }

  @Test
  fun testSucceedOfTraining() {
    assertNodeWithTextDoesNotExists(BRAVO_TEXT)
    playMove("e2", "e4")
    assertNodeWithTextExists(BRAVO_TEXT)
    Awaitility.awaitUntilTrue(TEST_TIMEOUT, "testSucceedOfTraining: Position not saved") {
      val positions = getAllPositions()
      positions.size == 1 &&
        positions[0].positionIdentifier == PositionIdentifier.START_POSITION &&
        positions[0].previousAndNextTrainingDate.previousDate.dayOfYear ==
          DateUtil.today().dayOfYear &&
        positions[0].previousAndNextTrainingDate.nextDate.dayOfYear ==
          NextDateCalculator.SUCCESS.calculateNextDate(
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
        positions[0].positionIdentifier == PositionIdentifier.START_POSITION &&
        positions[0].previousAndNextTrainingDate.previousDate.dayOfYear ==
          DateUtil.today().dayOfYear &&
        positions[0].previousAndNextTrainingDate.nextDate.dayOfYear ==
          NextDateCalculator.FAILURE.calculateNextDate(
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

  @Test
  fun testResetDayOnFail() {
    assertNodeWithTextDoesNotExists(BRAVO_TEXT)
    playMove("e2", "e3")
    assertNodeWithTextExists(BRAVO_TEXT)
    assertNodeWithTextExists("Increment a day").performClick()
    assertNodeWithTextDoesNotExists(BRAVO_TEXT)
    playMove("e2", "e4")
    assertNodeWithTextExists(BRAVO_TEXT)
    assertNodeWithTextExists("Increment a day").performClick()
    assertNodeWithTextExists("2")
    playMove("e2", "e3")
    assertNodeWithTextDoesNotExists(BRAVO_TEXT)
  }

  @Test
  fun testShowNextMove() {
    // Insert a second move in the database
    val game = Game()
    val startPos = game.position.createIdentifier()

    game.playMove("e4")
    val e4Pos = game.position.createIdentifier()
    val e4Move = StoredMove(startPos, e4Pos, "e4", isGood = true)
    game.playMove("e5")
    val e5Pos = game.position.createIdentifier()
    val e5Move = StoredMove(e4Pos, e5Pos, "e5", isGood = true)

    // Create the node with the move
    val testNode =
      StoredNode(
        positionIdentifier = e4Pos,
        PreviousAndNextMoves(listOf(e4Move), listOf(e5Move)),
        PreviousAndNextDate(DateUtil.dateInDays(-7), DateUtil.today()),
      )

    // Save the node to the database
    runTest { database.insertNodes(testNode) }

    // Ensure we rebuild the training page
    goToExplore()
    goToTraining()
    try {
      assertTileContainsPiece("e4", Pawn.white())
      playMove("e7", "e5")
    } catch (_: AssertionError) {
      playMove("e2", "e4")
    }
    assertNodeWithTextDoesNotExists(BRAVO_TEXT)
    try {
      assertTileContainsPiece("e4", Pawn.white())
      playMove("e7", "e5")
    } catch (_: AssertionError) {
      playMove("e2", "e4")
    }
    assertNodeWithTextExists(BRAVO_TEXT)
  }

  @Test
  fun testPromotion() {
    runTest { database.deleteAll(null) }
    val game = Game(PositionIdentifier("k7/7P/8/8/8/8/8/7K w KQkq"))
    val startPosition = game.position.createIdentifier()
    game.playMove("h8=Q+")
    val endPosition = game.position.createIdentifier()
    val startMove = StoredMove(startPosition, endPosition, "h8=Q", isGood = true)
    val node =
      StoredNode(
        positionIdentifier = startPosition,
        PreviousAndNextMoves(listOf(), listOf(startMove)),
        PreviousAndNextDate(DateUtil.dateInDays(-7), DateUtil.today()),
      )
    runTest { database.insertNodes(node) }
    goToExplore()
    goToTraining()
    assertNodeWithTextDoesNotExists(BRAVO_TEXT)
    playMove("h7", "h8")
    promoteTo(Piece.QUEEN)
    assertNodeWithTextExists(BRAVO_TEXT)
  }
}
