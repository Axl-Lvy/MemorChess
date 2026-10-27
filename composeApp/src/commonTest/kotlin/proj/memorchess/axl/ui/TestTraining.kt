package proj.memorchess.axl.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import org.koin.core.component.inject
import proj.memorchess.axl.core.config.REDUCE_MOTION_SETTING
import proj.memorchess.axl.core.config.SHORT_TERM_ENABLED_SETTING
import proj.memorchess.axl.core.config.TRAINING_MOVE_DELAY_SETTING
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.test_util.drainAllNodes
import proj.memorchess.axl.test_util.getTileDescription
import proj.memorchess.axl.ui.pages.Training
import proj.memorchess.axl.ui.util.hasClickLabel

private const val BRAVO_TEXT = "Bravo !"
private const val FAILED_TEXT = "FAILED"

@OptIn(ExperimentalTestApi::class)
class TestTraining : TestWithKoin() {

  private val database: DatabaseQueryManager by inject()

  private fun runTestFromSetup(
    cardState: CardState? = null,
    block: suspend ComposeUiTest.() -> Unit,
  ) = runComposeUiTest {
    koinSetUp()
    disableShortTermScheduler()
    try {
      resetDatabase(cardState ?: dueNowFromLastWeek())
      setTestContent { InitializeApp { Training() } }
      block()
    } finally {
      koinTearDown()
    }
  }

  /** Forces the long-term scheduler, so one answer finishes a single-card deck. */
  private fun disableShortTermScheduler() {
    SHORT_TERM_ENABLED_SETTING.setValue(false)
  }

  suspend fun resetDatabase(cardState: CardState = dueNowFromLastWeek()) {
    database.eraseAll()

    val engine = GameEngine()
    val startPos = engine.toPositionKey()

    engine.playSanMove("e4")
    val e4Pos = engine.toPositionKey()
    val e4Move = DataMove(startPos, e4Pos, "e4", isGood = true)

    // The scheduler filters on hasGoodOutgoing, so it must be set.
    val testNode =
      DataNode(
        positionKey = startPos,
        PreviousAndNextMoves(listOf(), listOf(e4Move)),
        cardState,
        // The new card queue orders by ascending depth, and this fixture must sort first.
        depth = 0,
        hasGoodOutgoing = true,
      )

    database.insertNodes(testNode)
  }

  /** Brand new card due now; failing it yields a one day interval under default FSRS 6 weights. */
  private fun dueNowFromLastWeek(): CardState {
    val now = DateUtil.now()
    return CardState(
      dueDate = now,
      lastReview = null,
      firstReview = null,
      stability = 0.0,
      difficulty = 0.0,
      reps = 0,
      lapses = 0,
    )
  }

  /**
   * Mature card due in two days; failing it reschedules it three days out under default FSRS 6
   * weights with fuzz off.
   */
  private fun matureReviewCardDueInTwoDays(): CardState {
    val now = DateUtil.now()
    return CardState(
      dueDate = now + 2.days,
      lastReview = now,
      firstReview = now - 30.days,
      stability = 60.0,
      difficulty = 5.0,
      reps = 5,
      lapses = 0,
      phase = CardPhase.REVIEW,
    )
  }

  @Test
  fun testSucceedOfTraining() = runTestFromSetup {
    assertNodeWithTextDoesNotExists(BRAVO_TEXT)
    playMove("e2", "e4")
    assertNodeWithTextExists(BRAVO_TEXT)
    waitUntilSuspending {
      val positions = drainAllNodes(database)
      val now = DateUtil.now()
      positions.size == 1 &&
        positions[0].positionKey == PositionKey.START_POSITION &&
        positions[0].cardState.dueDate > now &&
        positions[0].cardState.lapses == 0
    }
  }

  @Test
  fun testFailTraining() = runTestFromSetup {
    assertNodeWithTextDoesNotExists(FAILED_TEXT)
    playMove("e2", "e3")
    waitUntilSuspending { storedStartCard().lapses >= 1 }

    // The same position comes back for a retry, marked failed, until the right move is played.
    assertTileContainsPiece("e2", ChessPiece(PieceKind.PAWN, Player.WHITE))
    assertNodeWithTextExists(FAILED_TEXT)
    assertNodeWithTextDoesNotExists(BRAVO_TEXT)
    playMove("e2", "e3")
    assertTileContainsPiece("e2", ChessPiece(PieceKind.PAWN, Player.WHITE))
    // The FAIL counter counted the card once, not once per wrong move.
    waitUntilSuspending { onAllNodesWithText("1").fetchSemanticsNodes().isNotEmpty() }
    assertNodeWithTextDoesNotExists("2")
    playMove("e2", "e4")

    assertNodeWithTextExists(BRAVO_TEXT)
    storedStartCard().reps shouldBe 1
  }

  @Test
  fun testIncrementDay() = runTestFromSetup {
    assertNodeWithTextDoesNotExists(BRAVO_TEXT)
    playMove("e2", "e3")
    assertTileContainsPiece("e2", ChessPiece(PieceKind.PAWN, Player.WHITE))
    playMove("e2", "e4")
    assertNodeWithTextExists(BRAVO_TEXT)
    assertNodeWithTextExists("INCREMENT A DAY").performClick()
    assertNodeWithTextDoesNotExists(BRAVO_TEXT)
    playMove("e2", "e4")
    assertNodeWithTextExists("Days in advance: 1")
  }

  /**
   * The failed mature card is rescheduled past the window, so the deck empties and the counter
   * resets to one.
   */
  @Test
  fun testResetDayOnFail() =
    runTestFromSetup(matureReviewCardDueInTwoDays()) {
      // The card only surfaces two days in advance.
      assertNodeWithTextExists(BRAVO_TEXT)
      assertNodeWithTextExists("Days in advance: 0")
      assertNodeWithTextExists("INCREMENT A DAY").performClick()
      assertNodeWithTextExists("Days in advance: 1")
      assertNodeWithTextExists("INCREMENT A DAY").performClick()
      assertNodeWithTextDoesNotExists(BRAVO_TEXT)
      playMove("e2", "e3")
      assertTileContainsPiece("e2", ChessPiece(PieceKind.PAWN, Player.WHITE))
      playMove("e2", "e4")
      assertNodeWithTextExists(BRAVO_TEXT)
      assertNodeWithTextExists("Days in advance: 1")
      assertNodeWithTextDoesNotExists("Days in advance: 2")
    }

  @Test
  fun testHintAsksForConfirmationAndCancelLeavesTheCardUntouched() = runTestFromSetup {
    assertNodeWithTextExists("HINT").performClick()
    assertNodeWithTagExists("confirmDialog")

    assertNodeWithTagExists("confirmDialogCancelButton").performClick()

    assertNodeWithTagDoesNotExists("confirmDialog")
    assertNodeWithTextDoesNotExists(FAILED_TEXT)
    storedStartCard().reps shouldBe 0
  }

  @Test
  fun testRevealAsksForConfirmationAndCancelLeavesTheCardUntouched() = runTestFromSetup {
    assertNodeWithTextExists("REVEAL").performClick()
    assertNodeWithTagExists("confirmDialog")

    assertNodeWithTagExists("confirmDialogCancelButton").performClick()

    assertNodeWithTagDoesNotExists("confirmDialog")
    assertNodeWithTextDoesNotExists(FAILED_TEXT)
    storedStartCard().reps shouldBe 0
  }

  @Test
  fun testHintOnAnAlreadyFailedCardSkipsTheConfirmation() = runTestFromSetup {
    playMove("e2", "e3")
    assertTileContainsPiece("e2", ChessPiece(PieceKind.PAWN, Player.WHITE))
    assertNodeWithTextExists(FAILED_TEXT)

    assertNodeWithTextExists("HINT").performClick()
    assertNodeWithTextExists("REVEAL").performClick()

    assertNodeWithTagDoesNotExists("confirmDialog")
    storedStartCard().reps shouldBe 1
  }

  @Test
  fun testHintOnACardFailedByAHintSkipsTheConfirmation() = runTestFromSetup {
    confirmAssist("HINT")
    assertNodeWithTextExists(FAILED_TEXT)

    assertNodeWithTextExists("REVEAL").performClick()

    assertNodeWithTagDoesNotExists("confirmDialog")
  }

  @Test
  fun testHintGradesTheCardAgainOnce() = runTestFromSetup {
    confirmAssist("HINT")
    waitUntilSuspending { storedStartCard().reps == 1 }
    assertNodeWithTextExists(FAILED_TEXT)

    playMove("e2", "e4")

    assertNodeWithTextExists(BRAVO_TEXT)
    storedStartCard().reps shouldBe 1
  }

  @Test
  fun testRevealGradesTheCardAgainOnce() = runTestFromSetup {
    confirmAssist("REVEAL")
    waitUntilSuspending { storedStartCard().reps == 1 }
    assertNodeWithTextExists(FAILED_TEXT)

    playMove("e2", "e4")

    assertNodeWithTextExists(BRAVO_TEXT)
    storedStartCard().reps shouldBe 1
  }

  /**
   * An assisted card counts as forgotten, so it collapses the days-in-advance window like a fail.
   */
  @Test
  fun testResetDayOnAssistedSuccess() =
    runTestFromSetup(matureReviewCardDueInTwoDays()) {
      assertNodeWithTextExists("INCREMENT A DAY").performClick()
      assertNodeWithTextExists("INCREMENT A DAY").performClick()
      assertNodeWithTextDoesNotExists(BRAVO_TEXT)
      confirmAssist("REVEAL")
      waitUntilSuspending { storedStartCard().reps == 6 }

      playMove("e2", "e4")

      assertNodeWithTextExists(BRAVO_TEXT)
      assertNodeWithTextExists("Days in advance: 1")
    }

  /** Presses the HINT or REVEAL button labelled [label] and confirms the fail warning. */
  private suspend fun ComposeUiTest.confirmAssist(label: String) {
    assertNodeWithTextExists(label).performClick()
    assertNodeWithTagExists("confirmDialogOkButton").performClick()
  }

  private suspend fun storedStartCard(): CardState =
    drainAllNodes(database).single { it.positionKey == PositionKey.START_POSITION }.cardState

  @Test
  fun testShowNextMove() = runComposeUiTest {
    koinSetUp()
    disableShortTermScheduler()
    try {
      TRAINING_MOVE_DELAY_SETTING.setValue(Duration.ZERO)
      val engine = GameEngine()
      val startPos = engine.toPositionKey()

      engine.playSanMove("e4")
      val e4Pos = engine.toPositionKey()
      val e4Move = DataMove(startPos, e4Pos, "e4", isGood = true)
      engine.playSanMove("e5")
      val e5Pos = engine.toPositionKey()
      val e5Move = DataMove(e4Pos, e5Pos, "e5", isGood = true)

      val testNode =
        DataNode(
          positionKey = e4Pos,
          PreviousAndNextMoves(listOf(e4Move), listOf(e5Move)),
          dueNowFromLastWeek(),
          depth = 1,
          hasGoodOutgoing = true,
        )
      resetDatabase()
      database.insertNodes(testNode)
      setTestContent { InitializeApp { Training() } }

      try {
        assertTileContainsPiece("e4", ChessPiece(PieceKind.PAWN, Player.WHITE), Duration.ZERO)
        playMove("e7", "e5")
      } catch (_: Throwable) {
        playMove("e2", "e4")
      }
      assertNodeWithTextDoesNotExists(BRAVO_TEXT)
      try {
        assertTileContainsPiece("e4", ChessPiece(PieceKind.PAWN, Player.WHITE), Duration.ZERO)
        playMove("e7", "e5")
      } catch (_: Throwable) {
        playMove("e2", "e4")
      }
      assertNodeWithTextExists(BRAVO_TEXT)
    } finally {
      koinTearDown()
    }
  }

  @Test
  fun testPromotion() = runComposeUiTest {
    koinSetUp()
    disableShortTermScheduler()
    try {
      val engine = GameEngine(PositionKey("k7/7P/8/8/8/8/8/7K w KQkq"))
      val startPosition = engine.toPositionKey()
      engine.playSanMove("h8=Q+")
      val endPosition = engine.toPositionKey()
      val startMove = DataMove(startPosition, endPosition, "h8=Q", isGood = true)
      val node =
        DataNode(
          positionKey = startPosition,
          PreviousAndNextMoves(listOf(), listOf(startMove)),
          dueNowFromLastWeek(),
          hasGoodOutgoing = true,
        )
      database.eraseAll()
      database.insertNodes(node)
      setTestContent { InitializeApp { Training() } }
      assertNodeWithTextDoesNotExists(BRAVO_TEXT)
      playMove("h7", "h8")
      promoteTo("queen")
      assertNodeWithTextExists(BRAVO_TEXT)
    } finally {
      koinTearDown()
    }
  }

  /**
   * Clicks a tile without [proj.memorchess.axl.ui.clickOnTile]'s wait loop, which stalls while
   * [mainClock] is paused. Only safe on a settled board with the tile visible.
   */
  private fun ComposeUiTest.clickSettledTile(tileName: String) {
    onNode(hasClickLabel(getTileDescription(tileName))).performClick()
  }

  // The floater only shows during TRAINING_MOVE_DELAY_SETTING, which the test clock auto-advances
  // through on any node lookup, so the floater tests pause it before the move-completing click.

  @Test
  fun testPlusOneFloaterAppearsAfterACorrectMove() = runComposeUiTest {
    koinSetUp()
    disableShortTermScheduler()
    try {
      resetDatabase()
      setTestContent { InitializeApp { Training() } }
      assertNodeWithTextDoesNotExists(BRAVO_TEXT)

      clickOnTile("e2")
      mainClock.autoAdvance = false
      clickSettledTile("e4")
      mainClock.advanceTimeByFrame()
      assertNodeWithTagExists("training-plus-one")
      mainClock.autoAdvance = true
    } finally {
      koinTearDown()
    }
  }

  @Test
  fun testPlusOneFloaterDoesNotAppearAfterAWrongMove() = runComposeUiTest {
    koinSetUp()
    disableShortTermScheduler()
    try {
      resetDatabase()
      setTestContent { InitializeApp { Training() } }
      assertNodeWithTextDoesNotExists(BRAVO_TEXT)

      clickOnTile("e2")
      mainClock.autoAdvance = false
      clickSettledTile("e3") // wrong: e4 is the node's only good move
      mainClock.advanceTimeByFrame()
      assertNodeWithTagDoesNotExists("training-plus-one")
      mainClock.autoAdvance = true
    } finally {
      koinTearDown()
    }
  }

  @Test
  fun testPlusOneFloaterIsAnchoredOverThePlayedSquare() = runComposeUiTest {
    koinSetUp()
    disableShortTermScheduler()
    try {
      resetDatabase()
      setTestContent { InitializeApp { Training() } }
      assertNodeWithTextDoesNotExists(BRAVO_TEXT)

      clickOnTile("e2")
      mainClock.autoAdvance = false
      clickSettledTile("e4")
      // Measured bounds need the "+1" text, a string resource that loads asynchronously on wasmJs.
      val floaterBounds =
        advanceFramesUntilNodeExists(hasTestTag("training-plus-one") and hasText("+1"))
          .getBoundsInRoot()
      val tileBounds = onNode(hasClickLabel(getTileDescription("e4"))).getBoundsInRoot()
      floaterBounds.left shouldBeGreaterThanOrEqualTo tileBounds.left
      floaterBounds.right shouldBeLessThanOrEqualTo tileBounds.right
      floaterBounds.top shouldBeGreaterThanOrEqualTo tileBounds.top
      floaterBounds.bottom shouldBeLessThanOrEqualTo tileBounds.bottom
      mainClock.autoAdvance = true
    } finally {
      koinTearDown()
    }
  }

  @Test
  fun testPlusOneFloaterDoesNotAppearUnderReducedMotion() = runComposeUiTest {
    koinSetUp()
    disableShortTermScheduler()
    try {
      REDUCE_MOTION_SETTING.setValue(true)
      resetDatabase()
      setTestContent { InitializeApp { Training() } }
      assertNodeWithTextDoesNotExists(BRAVO_TEXT)

      clickOnTile("e2")
      mainClock.autoAdvance = false
      clickSettledTile("e4")
      mainClock.advanceTimeByFrame()
      assertNodeWithTagDoesNotExists("training-plus-one")
      mainClock.autoAdvance = true
    } finally {
      koinTearDown()
    }
  }
}
