package proj.memorchess.axl.core.interaction

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.interactions.SingleMoveTrainer
import proj.memorchess.axl.test_util.TestWithKoin

class TestSingleMoveTrainer : TestWithKoin() {
  private lateinit var singleMoveTrainer: SingleMoveTrainer
  private val database: DatabaseQueryManager by inject()
  private lateinit var testNode: DataNode

  override suspend fun setUp() {
    database.deleteAll(DateUtil.farInThePast())
    // Create a test node with some moves
    val engine = GameEngine()
    val startPosition = engine.toPositionKey()

    // Create e4 as a good move
    engine.playSanMove("e4")
    val e4Position = engine.toPositionKey()
    val e4Move = DataMove(startPosition, e4Position, "e4", isGood = true)

    // Create d4 as a bad move
    val engine2 = GameEngine()
    engine2.playSanMove("d4")
    val d4Position = engine2.toPositionKey()
    val d4Move = DataMove(startPosition, d4Position, "d4", isGood = false)

    // Create the node with both moves
    testNode =
      DataNode(
        positionKey = startPosition,
        PreviousAndNextMoves(listOf(), listOf(e4Move, d4Move)),
        PreviousAndNextDate(DateUtil.dateInDays(-7), DateUtil.today()),
      )

    database.insertNodes(testNode)

    singleMoveTrainer = SingleMoveTrainer(testNode) {}
  }

  @Test
  @Ignore
  fun testCorrectMove() = test {
    // FIXME: the settings need a main activity to be initialized

    // Play the good move e4
    clickOnTile("e2")
    clickOnTile("e4")

    // Verify the move was recognized as correct
    val updatedNode = database.getPosition(testNode.positionKey)
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

  @Test
  fun testIncorrectMove() = test {
    // Play the bad move d4
    clickOnTile("d2")
    clickOnTile("d4")

    // Verify the move was recognized as incorrect
    val updatedNode = database.getPosition(testNode.positionKey)
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

  @Test
  fun testUnknownMove() = test {
    // Play a move that's not in the node's next moves
    clickOnTile("c2")
    clickOnTile("c4")

    // Verify the move was recognized as incorrect
    val updatedNode = database.getPosition(testNode.positionKey)
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

  private suspend fun clickOnTile(tile: String) {
    val col = tile[0] - 'a'
    val row = tile[1] - '1'
    clickOnTile(Pair(row, col))
  }

  private suspend fun clickOnTile(coords: Pair<Int, Int>) {
    singleMoveTrainer.clickOnTile(coords)
  }
}
