package proj.memorchess.axl.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import org.koin.core.component.inject
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
import proj.memorchess.axl.ui.pages.Training

private const val BRAVO_TEXT = "Bravo !"

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
      setContent { InitializeApp { Training() } }
      block()
    } finally {
      koinTearDown()
    }
  }

  /**
   * Forces the long-term scheduler for these UI tests. They verify training interaction mechanics
   * (move play, feedback, promotion, the day counter) where one answer finishes a single-card deck.
   * With the short-term scheduler on (the production default) a card stays in the session on
   * sub-day learning steps until it graduates, so it never reaches the "Bravo!" screen after a
   * single answer. The in-session relearning behavior itself is covered by the scheduler and
   * algorithm unit tests; here it would only add timing flakiness.
   */
  private fun disableShortTermScheduler() {
    SHORT_TERM_ENABLED_SETTING.setValue(false)
  }

  suspend fun resetDatabase(cardState: CardState = dueNowFromLastWeek()) {
    // Delete all existing nodes
    database.eraseAll()

    // Create a test node with e4 as a good move
    val engine = GameEngine()
    val startPos = engine.toPositionKey()

    engine.playSanMove("e4")
    val e4Pos = engine.toPositionKey()
    val e4Move = DataMove(startPos, e4Pos, "e4", isGood = true)

    // Create the node with the move. The good outgoing e4 edge makes it trainable, so the derived
    // hasGoodOutgoing projection the scheduler filters on must be set.
    val testNode =
      DataNode(
        positionKey = startPos,
        PreviousAndNextMoves(listOf(), listOf(e4Move)),
        cardState,
        hasGoodOutgoing = true,
      )

    // Save the node to the database
    database.insertNodes(testNode)
  }

  /**
   * Brand new card due immediately. Failure on a new card maps to a one day interval under FSRS 6
   * with default weights, matching the UI assertions that check the day increment counter.
   */
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
   * Graduated card due in two days, mature enough (high stability) that failing it reschedules it
   * three days out under FSRS 6 with default weights and fuzz off. Drives the day counter reset
   * scenario: the card only surfaces when training two days in advance, and a failure there pushes
   * it beyond the window again, emptying the deck.
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
    assertNodeWithTextDoesNotExists(BRAVO_TEXT)
    playMove("e2", "e3")
    // No manual click — Training auto-advances Anki-style on wrong moves. With only one card in
    // the deck the "no more cards" Bravo screen appears once the wrong move is graded AGAIN.
    assertNodeWithTextExists(BRAVO_TEXT)
    waitUntilSuspending {
      val positions = drainAllNodes(database)
      positions.size == 1 &&
        positions[0].positionKey == PositionKey.START_POSITION &&
        positions[0].cardState.lapses >= 1
    }
  }

  @Test
  fun testIncrementDay() = runTestFromSetup {
    assertNodeWithTextDoesNotExists(BRAVO_TEXT)
    playMove("e2", "e3")
    // Wrong move auto-advances; with only one card the Bravo screen appears.
    assertNodeWithTextExists(BRAVO_TEXT)
    assertNodeWithTextExists("INCREMENT A DAY").performClick()
    assertNodeWithTextDoesNotExists(BRAVO_TEXT)
    playMove("e2", "e4")
    assertNodeWithTextExists("Days in advance: 1")
  }

  /**
   * Verifies the day counter reset behavior on a failed review: failing a card while training two
   * days in advance collapses the counter back to one. The card is mature, so the failure
   * reschedules it beyond the two day window and empties the deck, which is what triggers the
   * reset.
   */
  @Test
  fun testResetDayOnFail() =
    runTestFromSetup(matureReviewCardDueInTwoDays()) {
      // Nothing is due today nor tomorrow: the card only surfaces two days in advance.
      assertNodeWithTextExists(BRAVO_TEXT)
      assertNodeWithTextExists("Days in advance: 0")
      assertNodeWithTextExists("INCREMENT A DAY").performClick()
      assertNodeWithTextExists("Days in advance: 1")
      assertNodeWithTextExists("INCREMENT A DAY").performClick()
      assertNodeWithTextDoesNotExists(BRAVO_TEXT)
      playMove("e2", "e3")
      // The failed card is rescheduled past the window, so the deck empties and the counter
      // collapses back to one instead of staying at two.
      assertNodeWithTextExists(BRAVO_TEXT)
      assertNodeWithTextExists("Days in advance: 1")
      assertNodeWithTextDoesNotExists("Days in advance: 2")
    }

  @Test
  fun testShowNextMove() = runComposeUiTest {
    koinSetUp()
    disableShortTermScheduler()
    try {
      TRAINING_MOVE_DELAY_SETTING.setValue(Duration.ZERO)
      // Insert a second move in the database
      val engine = GameEngine()
      val startPos = engine.toPositionKey()

      engine.playSanMove("e4")
      val e4Pos = engine.toPositionKey()
      val e4Move = DataMove(startPos, e4Pos, "e4", isGood = true)
      engine.playSanMove("e5")
      val e5Pos = engine.toPositionKey()
      val e5Move = DataMove(e4Pos, e5Pos, "e5", isGood = true)

      // Create the node with the move
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
      setContent { InitializeApp { Training() } }

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
      setContent { InitializeApp { Training() } }
      assertNodeWithTextDoesNotExists(BRAVO_TEXT)
      playMove("h7", "h8")
      promoteTo("queen")
      assertNodeWithTextExists(BRAVO_TEXT)
    } finally {
      koinTearDown()
    }
  }
}
