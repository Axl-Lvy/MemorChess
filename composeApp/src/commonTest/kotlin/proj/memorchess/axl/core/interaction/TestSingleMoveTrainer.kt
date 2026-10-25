package proj.memorchess.axl.core.interaction

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.engine.BoardLocation
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.interactions.SingleMoveTrainer
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState
import proj.memorchess.axl.test_util.TestWithKoin

class TestSingleMoveTrainer : TestWithKoin() {
  private lateinit var singleMoveTrainer: SingleMoveTrainer
  private val database: DatabaseQueryManager by inject()
  private val treeStore: TreeStore by inject()
  private val tagStore: RepertoireTagStore by inject()
  private lateinit var testNode: DataNode
  private var lastPlayedSquare: BoardLocation? = null
  private var lastCorrectSquare: BoardLocation? = null
  private var lastEdge: proj.memorchess.axl.core.graph.Edge? = null

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
    val node = treeStore.node(testNode.positionKey)
    checkNotNull(node)
    singleMoveTrainer =
      SingleMoveTrainer(node) { edge, played, correct ->
        lastEdge = edge
        lastPlayedSquare = played
        lastCorrectSquare = correct
      }
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
    assertEquals(BoardLocation(3, 4), lastPlayedSquare) // e4
  }

  @Test
  fun testIncorrectMove() = test {
    clickOnTile("d2")
    clickOnTile("d4")

    val updatedNode = database.getPosition(testNode.positionKey)
    assertNotNull(updatedNode)
    assertTrue(updatedNode.cardState.lapses >= 1, "Lapses should increase on incorrect review")
    assertEquals(testNode.cardState.reps + 1, updatedNode.cardState.reps)
    assertEquals(BoardLocation(3, 3), lastPlayedSquare) // d4
    assertEquals(BoardLocation(3, 4), lastCorrectSquare) // e4, the node's good edge
  }

  @Test
  fun testUnknownMove() = test {
    clickOnTile("c2")
    clickOnTile("c4")

    val updatedNode = database.getPosition(testNode.positionKey)
    assertNotNull(updatedNode)
    assertTrue(updatedNode.cardState.lapses >= 1, "Lapses should increase on unknown move")
    assertEquals(testNode.cardState.reps + 1, updatedNode.cardState.reps)
    assertEquals(BoardLocation(3, 2), lastPlayedSquare) // c4, matched no edge at all
    assertEquals(BoardLocation(3, 4), lastCorrectSquare) // e4, the node's good edge still resolves
  }

  @Test
  fun aScopedTrainerRejectsACorrectMoveTaggedWithADifferentRepertoire() = test {
    val e4Position = GameEngine().apply { playSanMove("e4") }.toPositionKey()
    tagStore.tag(testNode.positionKey, e4Position, "ruy-lopez")
    var correctEdge: proj.memorchess.axl.core.graph.Edge? = null
    var correctSquare: BoardLocation? = null
    val node = treeStore.node(testNode.positionKey)
    checkNotNull(node)
    val scopedTrainer =
      SingleMoveTrainer(node, repertoireScope = "italian-game") { edge, _, correct ->
        correctEdge = edge
        correctSquare = correct
      }

    tileClick(scopedTrainer, "e2")
    tileClick(scopedTrainer, "e4")

    assertEquals(null, correctEdge) // rejected: correct chess move, wrong repertoire
    assertNull(correctSquare) // no edge is tagged for "italian-game", so nothing to outline
  }

  @Test
  fun hintReturnsTheExpectedMoveOriginSquareAndGradesAgain() = test {
    singleMoveTrainer.hint() shouldBe BoardLocation(1, 4) // e2

    val card = storedCard()
    card.reps shouldBe testNode.cardState.reps + 1
    card.lapses shouldBe testNode.cardState.lapses + 1
  }

  @Test
  fun revealReturnsTheExpectedMoveOriginAndDestinationAndGradesAgain() = test {
    singleMoveTrainer.reveal() shouldBe Pair(BoardLocation(1, 4), BoardLocation(3, 4)) // e2-e4

    val card = storedCard()
    card.reps shouldBe testNode.cardState.reps + 1
    card.lapses shouldBe testNode.cardState.lapses + 1
  }

  @Test
  fun aCorrectMoveAfterAHintIsAcceptedButNotGradedAgain() = test {
    singleMoveTrainer.hint()

    clickOnTile("e2")
    clickOnTile("e4")

    lastEdge.shouldNotBeNull().move shouldBe "e4"
    val card = storedCard()
    card.reps shouldBe testNode.cardState.reps + 1
    card.lapses shouldBe testNode.cardState.lapses + 1
  }

  @Test
  fun aWrongMoveAfterARevealIsNotGradedAgain() = test {
    singleMoveTrainer.reveal()

    clickOnTile("d2")
    clickOnTile("d4")

    lastEdge.shouldBeNull()
    storedCard().reps shouldBe testNode.cardState.reps + 1
  }

  @Test
  fun aHintThenARevealGradesTheCardOnce() = test {
    singleMoveTrainer.hint()
    singleMoveTrainer.reveal().shouldNotBeNull()

    storedCard().reps shouldBe testNode.cardState.reps + 1
  }

  @Test
  fun aHintAfterTheMoveWasPlayedReturnsNothingAndDoesNotGrade() = test {
    clickOnTile("e2")
    clickOnTile("e4")
    val afterMove = storedCard()

    singleMoveTrainer.hint().shouldBeNull()
    singleMoveTrainer.reveal().shouldBeNull()

    storedCard() shouldBe afterMove
  }

  @Test
  fun aScopedTrainerWithNoInScopeGoodMoveHintsNothingAndDoesNotGrade() = test {
    val node = treeStore.node(testNode.positionKey)
    checkNotNull(node)
    val scopedTrainer = SingleMoveTrainer(node, repertoireScope = "italian-game") { _, _, _ -> }

    scopedTrainer.hint().shouldBeNull()
    scopedTrainer.reveal().shouldBeNull()

    storedCard() shouldBe testNode.cardState
  }

  @Test
  fun swappingTheNodeClearsTheAssistedState() = test {
    val e4Position = GameEngine().apply { playSanMove("e4") }.toPositionKey()
    val e5Position = GameEngine().apply { playSanMove("e4"); playSanMove("e5") }.toPositionKey()
    val e4Node =
      DataNode(
        positionKey = e4Position,
        PreviousAndNextMoves(listOf(), listOf(DataMove(e4Position, e5Position, "e5", isGood = true))),
        testNode.cardState,
      )
    database.insertNodes(e4Node)
    singleMoveTrainer.hint()
    singleMoveTrainer.updateNode(checkNotNull(treeStore.node(e4Position)))

    singleMoveTrainer.hint() shouldBe BoardLocation(6, 4) // e7

    val card = checkNotNull(database.getPosition(e4Position)).cardState
    card.reps shouldBe testNode.cardState.reps + 1
  }

  private suspend fun storedCard(): CardState =
    checkNotNull(database.getPosition(testNode.positionKey)).cardState

  private suspend fun tileClick(trainer: SingleMoveTrainer, tile: String) {
    val col = tile[0] - 'a'
    val row = tile[1] - '1'
    trainer.clickOnTile(Pair(row, col))
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
