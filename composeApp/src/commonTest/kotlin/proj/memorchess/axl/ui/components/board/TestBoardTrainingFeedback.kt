package proj.memorchess.axl.ui.components.board

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import proj.memorchess.axl.core.engine.BoardLocation
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.interactions.InteractionsManager
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.components.training.BoardContainer
import proj.memorchess.axl.ui.setKineticContent

/**
 * Pins that "no graded move yet" is carried in [BoardTrainingFeedback.isCorrect] itself, rather
 * than as a convention every reader of the flag has to reapply.
 *
 * Finding: [BoardTrainingFeedback] used to default `isCorrect` to `true`, so a consumer that read
 * the flag without ALSO checking [BoardTrainingFeedback.playedSquare] or
 * [BoardTrainingFeedback.attempt] would silently treat "nothing played yet" as "correct". Making
 * `isCorrect` nullable turns that omission into a compile error at every existing consumer, and
 * these tests pin the resulting behaviour at each one of [BoardGrid]'s consumers plus
 * [proj.memorchess.axl.ui.components.training.BoardContainer]'s.
 */
@OptIn(ExperimentalTestApi::class)
class TestBoardTrainingFeedback : TestWithKoin() {

  @BeforeTest
  fun before() {
    koinSetUp()
  }

  @AfterTest
  fun after() {
    koinTearDown()
  }

  // The core fix: absence must be readable straight off the default, not inferred by convention.

  @Test
  fun defaultFeedbackCarriesNoCorrectnessVerdict() {
    BoardTrainingFeedback().isCorrect shouldBe null
  }

  // CONSUMER 1 — BoardGrid's plus-one LaunchedEffect + PlusOneFloater composition (isCorrect read
  // twice: once to snap/animate the floater's alpha, once to decide whether to compose it at all).

  @Test
  fun plusOneFloaterIsAbsentWhenNothingHasBeenPlayedYet() = runComposeUiTest {
    setKineticContent {
      BoardGrid(state = testBoardGridState(), feedback = BoardTrainingFeedback())
    }
    onNodeWithTag(PLUS_ONE_TAG).assertDoesNotExist()
  }

  @Test
  fun plusOneFloaterStaysAbsentWhenAMoveLandedButGradingIsAbsent() = runComposeUiTest {
    // A played square with no verdict: the exact shape the old `Boolean = true` default made
    // indistinguishable from a genuine correct answer.
    setKineticContent {
      BoardGrid(
        state = testBoardGridState(),
        feedback = BoardTrainingFeedback(playedSquare = E4, isCorrect = null, attempt = 1),
      )
    }
    onNodeWithTag(PLUS_ONE_TAG).assertDoesNotExist()
  }

  @Test
  fun plusOneFloaterAppearsOnAGenuineCorrectAnswer() = runComposeUiTest {
    setKineticContent {
      BoardGrid(
        state = testBoardGridState(),
        feedback = BoardTrainingFeedback(playedSquare = E4, isCorrect = true, attempt = 1),
      )
    }
    onNodeWithTag(PLUS_ONE_TAG).assertExists()
  }

  @Test
  fun plusOneFloaterStaysAbsentOnAWrongAnswer() = runComposeUiTest {
    setKineticContent {
      BoardGrid(
        state = testBoardGridState(),
        feedback =
          BoardTrainingFeedback(
            playedSquare = E4,
            correctSquare = E2,
            isCorrect = false,
            attempt = 1,
          ),
      )
    }
    onNodeWithTag(PLUS_ONE_TAG).assertDoesNotExist()
  }

  // CONSUMERS 2 & 3 — DrawTileGrid's PlayedSquareOverlay dispatch and the animateTileFeedback
  // correct/wrong branches. Neither exposes a semantics node, so these are composition smoke tests:
  // they pin that a null isCorrect (with or without a played square) never throws, matching the
  // repo's "smoke tests prove composition completes" precedent for draw-phase-only feedback.

  @Test
  fun boardGridComposesWhenNoMoveHasBeenPlayedYet() = runComposeUiTest {
    setKineticContent {
      BoardGrid(state = testBoardGridState(), feedback = BoardTrainingFeedback())
    }
  }

  @Test
  fun boardGridComposesWhenAMoveLandedButGradingIsAbsent() = runComposeUiTest {
    setKineticContent {
      BoardGrid(
        state = testBoardGridState(),
        feedback = BoardTrainingFeedback(playedSquare = E4, isCorrect = null, attempt = 1),
      )
    }
  }

  @Test
  fun boardGridComposesOnACorrectAnswer() = runComposeUiTest {
    setKineticContent {
      BoardGrid(
        state = testBoardGridState(),
        feedback = BoardTrainingFeedback(playedSquare = E4, isCorrect = true, attempt = 1),
      )
    }
  }

  @Test
  fun boardGridComposesOnAWrongAnswer() = runComposeUiTest {
    setKineticContent {
      BoardGrid(
        state = testBoardGridState(),
        feedback =
          BoardTrainingFeedback(
            playedSquare = E4,
            correctSquare = E2,
            isCorrect = false,
            attempt = 1,
          ),
      )
    }
  }

  // CONSUMER 4 — BoardContainer's registeredFlash border, which reduces isCorrect to a plain
  // Boolean for its `success` parameter. Never throws regardless of the verdict, absent included.

  @Test
  fun boardContainerComposesWhenAMoveLandedButGradingIsAbsent() = runComposeUiTest {
    setKineticContent {
      BoardContainer(
        inverted = false,
        trainer = TestInteractionsManager(),
        feedback = BoardTrainingFeedback(playedSquare = E4, isCorrect = null, attempt = 1),
      )
    }
  }

  @Test
  fun boardContainerComposesOnACorrectAnswer() = runComposeUiTest {
    setKineticContent {
      BoardContainer(
        inverted = false,
        trainer = TestInteractionsManager(),
        feedback = BoardTrainingFeedback(playedSquare = E4, isCorrect = true, attempt = 1),
      )
    }
  }

  private fun testBoardGridState() =
    BoardGridState(inverted = false, interactionsManager = TestInteractionsManager())

  private class TestInteractionsManager : InteractionsManager(GameEngine()) {
    override suspend fun afterPlayMove(move: String) {}
  }

  private companion object {
    const val PLUS_ONE_TAG = "training-plus-one"
    val E4 = BoardLocation(3, 4)
    val E2 = BoardLocation(1, 4)
  }
}
