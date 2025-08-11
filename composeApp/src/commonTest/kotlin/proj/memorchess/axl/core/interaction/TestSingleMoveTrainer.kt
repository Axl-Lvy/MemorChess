package proj.memorchess.axl.core.interaction

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.board.IBoard
import proj.memorchess.axl.core.engine.moves.factory.DummyCheckChecker
import proj.memorchess.axl.core.engine.moves.factory.RealMoveFactory
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves
import proj.memorchess.axl.core.interactions.SingleMoveTrainer
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.components.popup.ToastRendererHolder

class TestSingleMoveTrainer : TestWithKoin {
  private lateinit var singleMoveTrainer: SingleMoveTrainer
  private lateinit var moveFactory: RealMoveFactory
  private lateinit var checkChecker: DummyCheckChecker
  private val database: DatabaseQueryManager by inject()
  private lateinit var testNode: StoredNode

  private fun initialize() = runTest {
    database.deleteAll(DateUtil.farInThePast())
    // Create a test node with some moves
    val game = Game()
    val startPosition = game.position.createIdentifier()

    // Create e4 as a good move
    game.playMove("e4")
    val e4Position = game.position.createIdentifier()
    val e4Move = StoredMove(startPosition, e4Position, "e4", isGood = true)

    // Create d4 as a bad move
    val game2 = Game()
    game2.playMove("d4")
    val d4Position = game2.position.createIdentifier()
    val d4Move = StoredMove(startPosition, d4Position, "d4", isGood = false)

    // Create the node with both moves
    testNode =
      StoredNode(
        positionIdentifier = startPosition,
        PreviousAndNextMoves(listOf(), listOf(e4Move, d4Move)),
        PreviousAndNextDate(DateUtil.dateInDays(-7), DateUtil.today()),
      )

    testNode.save()

    ToastRendererHolder.init { _, _ -> }
    singleMoveTrainer = SingleMoveTrainer(testNode) {}
    moveFactory = RealMoveFactory(singleMoveTrainer.game.position)
    checkChecker = DummyCheckChecker(singleMoveTrainer.game.position)
  }

  @Test
  @Ignore
  fun testCorrectMove() {
    // FIXME: the settings need a main activity to be initialized
    initialize()

    // Play the good move e4
    clickOnTile("e2")
    clickOnTile("e4")

    // Verify the move was recognized as correct
    runTest {
      val updatedNode = database.getPosition(testNode.positionIdentifier)
      // The next training date should be further in the future (success case)
      assertTrue(
        updatedNode!!.previousAndNextTrainingDate.nextDate > DateUtil.tomorrow(),
        "Next training date should be more than 1 day in the future for correct move",
      )
      assertEquals(
        DateUtil.today(),
        updatedNode.previousAndNextTrainingDate.previousDate,
        "Last trained date should be updated to today",
      )
    }
  }

  @Test
  fun testIncorrectMove() {
    initialize()

    // Play the bad move d4
    clickOnTile("d2")
    clickOnTile("d4")

    // Verify the move was recognized as incorrect
    runTest {
      val updatedNode = database.getPosition(testNode.positionIdentifier)
      // The next training date should be tomorrow (failure case)
      assertEquals(
        DateUtil.tomorrow(),
        updatedNode!!.previousAndNextTrainingDate.nextDate,
        "Next training date should be tomorrow for incorrect move",
      )
      assertEquals(
        DateUtil.today(),
        updatedNode.previousAndNextTrainingDate.previousDate,
        "Last trained date should be updated to today",
      )
    }
  }

  @Test
  fun testUnknownMove() {
    initialize()

    // Play a move that's not in the node's next moves
    clickOnTile("c2")
    clickOnTile("c4")

    // Verify the move was recognized as incorrect
    runTest {
      val updatedNode = database.getPosition(testNode.positionIdentifier)
      // The next training date should be tomorrow (failure case)
      assertEquals(
        DateUtil.tomorrow(),
        updatedNode!!.previousAndNextTrainingDate.nextDate,
        "Next training date should be tomorrow for unknown move",
      )
      assertEquals(
        DateUtil.today(),
        updatedNode.previousAndNextTrainingDate.previousDate,
        "Last trained date should be updated to today",
      )
    }
  }

  private fun clickOnTile(tile: String) {
    clickOnTile(IBoard.Companion.getCoords(tile))
  }

  private fun clickOnTile(coords: Pair<Int, Int>) {
    runTest { singleMoveTrainer.clickOnTile(coords) }
  }
}
