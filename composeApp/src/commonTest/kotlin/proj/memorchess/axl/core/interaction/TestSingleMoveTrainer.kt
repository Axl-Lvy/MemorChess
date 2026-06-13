package proj.memorchess.axl.core.interaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.interactions.SingleMoveTrainer
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState
import proj.memorchess.axl.test_util.TestWithKoin

class TestSingleMoveTrainer : TestWithKoin() {
  private lateinit var singleMoveTrainer: SingleMoveTrainer
  private val database: DatabaseQueryManager by inject()
  private val treeStore: TreeStore by inject()
  private lateinit var testNode: DataNode

  override suspend fun setUp() {
    database.eraseAll()
    val engine = GameEngine()
    val startPosition = engine.toPositionKey()

    engine.playSanMove("e4")
    val e4Position = engine.toPositionKey()
    val e4Move = DataMove(startPosition, e4Position, "e4", isGood = true)

    val engine2 = GameEngine()
    engine2.playSanMove("d4")
    val d4Position = engine2.toPositionKey()
    val d4Move = DataMove(startPosition, d4Position, "d4", isGood = false)

    val now = DateUtil.now()
    val sevenDaysAgo = now - 7.days
    val cardState =
      CardState(
        dueDate = now,
        lastReview = sevenDaysAgo,
        firstReview = sevenDaysAgo,
        stability = 7.0,
        difficulty = 5.0,
        reps = 1,
        lapses = 0,
        // A graduated card due for review: a wrong answer is a genuine lapse (REVIEW ->
        // relearning).
        phase = CardPhase.REVIEW,
      )

    testNode =
      DataNode(
        positionKey = startPosition,
        PreviousAndNextMoves(listOf(), listOf(e4Move, d4Move)),
        cardState,
      )

    database.insertNodes(testNode)
    treeStore.load()
    val node = treeStore.current().get(testNode.positionKey)
    checkNotNull(node)
    singleMoveTrainer = SingleMoveTrainer(node) {}
  }

  @Test
  fun testCorrectMove() = test {
    clickOnTile("e2")
    clickOnTile("e4")

    val updatedNode = database.getPosition(testNode.positionKey)
    assertNotNull(updatedNode)
    val due = updatedNode.cardState.dueDate
    assertTrue(
      due > DateUtil.now() + 1.days,
      "Next due date should be more than 1 day in the future for a correct review",
    )
  }

  @Test
  fun testIncorrectMove() = test {
    clickOnTile("d2")
    clickOnTile("d4")

    val updatedNode = database.getPosition(testNode.positionKey)
    assertNotNull(updatedNode)
    assertTrue(updatedNode.cardState.lapses >= 1, "Lapses should increase on incorrect review")
    assertEquals(testNode.cardState.reps + 1, updatedNode.cardState.reps)
  }

  @Test
  fun testUnknownMove() = test {
    clickOnTile("c2")
    clickOnTile("c4")

    val updatedNode = database.getPosition(testNode.positionKey)
    assertNotNull(updatedNode)
    assertTrue(updatedNode.cardState.lapses >= 1, "Lapses should increase on unknown move")
    assertEquals(testNode.cardState.reps + 1, updatedNode.cardState.reps)
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
