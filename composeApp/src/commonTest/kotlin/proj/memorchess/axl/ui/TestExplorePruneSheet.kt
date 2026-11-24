package proj.memorchess.axl.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DESCENDANT_COUNT_CAP
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.test_util.drainAllNodes
import proj.memorchess.axl.ui.pages.Explore
import proj.memorchess.axl.ui.util.hasClickLabel

@OptIn(ExperimentalTestApi::class)
class TestExplorePruneSheet : TestWithKoin() {

  private val database: DatabaseQueryManager by inject()

  private val markedForPruning =
    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Marked for pruning")

  private fun runTestFromSetup(block: suspend ComposeUiTest.() -> Unit) = runComposeUiTest {
    koinSetUp()
    try {
      setTestContent { InitializeApp { Explore() } }
      block()
    } finally {
      koinTearDown()
    }
  }

  /** Key of the `index`th node in a chain rooted at [PositionKey.START_POSITION]. */
  private fun chainKey(index: Int): PositionKey =
    if (index == 0) PositionKey.START_POSITION else PositionKey("prune chain $index")

  private fun chainMove(from: Int, to: Int): DataMove =
    DataMove(chainKey(from), chainKey(to), "m$from-$to", isGood = true)

  /**
   * Builds a linear chain of [length] nodes rooted at [PositionKey.START_POSITION], each with
   * exactly one child, so a prune from the root removes exactly [length] positions. The first
   * [reviewed] nodes carry review history.
   */
  private fun buildDescendantChain(length: Int, reviewed: Int = 0): List<DataNode> =
    (0 until length).map { index ->
      val incoming = if (index == 0) emptyList() else listOf(chainMove(index - 1, index))
      val outgoing = if (index == length - 1) emptyList() else listOf(chainMove(index, index + 1))
      val card = CardStateFactory.new()
      val reviewedCard =
        if (index < reviewed) card.copy(lastReview = Instant.fromEpochSeconds(1_000)) else card
      DataNode(chainKey(index), PreviousAndNextMoves(incoming, outgoing), reviewedCard)
    }

  private suspend fun insertChain(length: Int, reviewed: Int = 0) {
    database.insertNodes(*buildDescendantChain(length, reviewed).toTypedArray())
  }

  private suspend fun ComposeUiTest.openSelectStep() {
    waitUntilNodeExists(hasContentDescription("Delete")).performClick()
    assertNodeWithTagExists("pruneSelectSheet")
  }

  private suspend fun ComposeUiTest.openConfirmStep() {
    openSelectStep()
    assertNodeWithTagExists("pruneSelectSheetContinueButton").performClick()
    assertNodeWithTagExists("pruneConfirmSheet")
  }

  private suspend fun ComposeUiTest.tapScrim() {
    // Top-left corner of the full-screen scrim: always clear of the bottom-anchored panel.
    waitUntilNodeExists(hasClickLabel("Close")).performTouchInput { click(Offset(1f, 1f)) }
  }

  @Test
  fun testDeleteButtonOpensSelectStepWithoutDeleting() = runTestFromSetup {
    insertChain(1)

    openSelectStep()

    assertNodeWithTextExists("Prune this branch?")
    assertNodeWithTagDoesNotExists("pruneConfirmSheetOkButton")
    assertNotNull(database.getPosition(PositionKey.START_POSITION))
  }

  @Test
  fun testContinueShowsConfirmStepWithCountHeadline() = runTestFromSetup {
    insertChain(1)

    openConfirmStep()

    assertNodeWithTextExists("Remove 1 position?")
    assertNodeWithTagDoesNotExists("pruneSelectSheetContinueButton")
  }

  @Test
  fun testCancelAtSelectStepDismissesWithoutDeleting() = runTestFromSetup {
    insertChain(1)
    openSelectStep()

    assertNodeWithTagExists("pruneSelectSheetCancelButton").performClick()

    waitUntilNodeDoesNotExist(hasTestTag("pruneSelectSheet"))
    assertNotNull(database.getPosition(PositionKey.START_POSITION))
  }

  @Test
  fun testCancelAtConfirmStepDismissesWithoutDeleting() = runTestFromSetup {
    insertChain(1)
    openConfirmStep()

    assertNodeWithTagExists("pruneConfirmSheetCancelButton").performClick()

    waitUntilNodeDoesNotExist(hasTestTag("pruneConfirmSheet"))
    assertNotNull(database.getPosition(PositionKey.START_POSITION))
  }

  @Test
  fun testReopeningAfterCancelAtConfirmStepStartsAtSelectStep() = runTestFromSetup {
    insertChain(1)
    openConfirmStep()
    assertNodeWithTagExists("pruneConfirmSheetCancelButton").performClick()
    waitUntilNodeDoesNotExist(hasTestTag("pruneConfirmSheet"))

    openSelectStep()

    assertNodeWithTagDoesNotExists("pruneConfirmSheet")
  }

  @Test
  fun testConfirmDeletesTheCurrentNode() = runTestFromSetup {
    insertChain(1)
    openConfirmStep()

    assertNodeWithTagExists("pruneConfirmSheetOkButton").performClick()

    waitUntilSuspending { database.getPosition(PositionKey.START_POSITION) == null }
  }

  @Test
  fun testTappingSheetBodyDoesNotDismissSheet() = runTestFromSetup {
    // Regression test: a tap on the panel's text or padding must not fall through to the scrim.
    insertChain(1)
    openConfirmStep()

    assertNodeWithTextExists("Remove 1 position?").performClick()

    assertNodeWithTagExists("pruneConfirmSheet")
  }

  @Test
  fun testTappingScrimAtSelectStepDismissesWithoutDeleting() = runTestFromSetup {
    insertChain(1)
    openSelectStep()

    tapScrim()

    waitUntilNodeDoesNotExist(hasTestTag("pruneSelectSheet"))
    assertNotNull(database.getPosition(PositionKey.START_POSITION))
  }

  @Test
  fun testTappingScrimAtConfirmStepDismissesWithoutDeleting() = runTestFromSetup {
    insertChain(1)
    openConfirmStep()

    tapScrim()

    waitUntilNodeDoesNotExist(hasTestTag("pruneConfirmSheet"))
    assertNotNull(database.getPosition(PositionKey.START_POSITION))
  }

  @Test
  fun testZeroDescendantsShowsZeroCountWithLiveDeleteButton() = runTestFromSetup {
    // No node saved at the current position: nothing is removed, and the plural "other" arm (not
    // "one") reads "Remove 0 positions?". The delete button must still work.
    openConfirmStep()

    assertNodeWithTextExists("Remove 0 positions?")
    assertNodeWithTextExists("None of them has review history yet.")
    assertNodeWithTagExists("pruneConfirmSheetOkButton").assertIsEnabled().performClick()
    waitUntilNodeDoesNotExist(hasTestTag("pruneConfirmSheet"))
    assertNull(database.getPosition(PositionKey.START_POSITION))
  }

  @Test
  fun testTwoDescendantsUsesPluralOtherText() = runTestFromSetup {
    insertChain(2)

    openConfirmStep()

    assertNodeWithTextExists("Remove 2 positions?")
  }

  @Test
  fun testDescendantCountJustBelowCapShowsPluralText() = runTestFromSetup {
    insertChain(DESCENDANT_COUNT_CAP - 1)

    openConfirmStep()

    assertNodeWithTextExists("Remove ${DESCENDANT_COUNT_CAP - 1} positions?")
  }

  @Test
  fun testDescendantCountAtCapShowsCappedText() = runTestFromSetup {
    insertChain(DESCENDANT_COUNT_CAP)

    openConfirmStep()

    assertNodeWithTextExists("Remove $DESCENDANT_COUNT_CAP or more positions?")
  }

  @Test
  fun testNoReviewedPositionStatesNoHistory() = runTestFromSetup {
    insertChain(2)

    openConfirmStep()

    assertNodeWithTextExists("None of them has review history yet.")
  }

  @Test
  fun testOneReviewedPositionUsesSingularLine() = runTestFromSetup {
    insertChain(2, reviewed = 1)

    openConfirmStep()

    assertNodeWithTextExists("1 of them has review history you already earned.")
  }

  @Test
  fun testTwoReviewedPositionsUsePluralLine() = runTestFromSetup {
    insertChain(2, reviewed = 2)

    openConfirmStep()

    assertNodeWithTextExists("2 of them have review history you already earned.")
  }

  @Test
  fun testReviewedJustBelowCapIsExact() = runTestFromSetup {
    val count = DESCENDANT_COUNT_CAP - 1
    insertChain(count, reviewed = count)

    openConfirmStep()

    assertNodeWithTextExists("$count of them have review history you already earned.")
  }

  @Test
  fun testReviewedAtCapIsALowerBound() = runTestFromSetup {
    insertChain(DESCENDANT_COUNT_CAP, reviewed = DESCENDANT_COUNT_CAP)

    openConfirmStep()

    assertNodeWithTextExists(
      "At least $DESCENDANT_COUNT_CAP of them have review history you already earned.",
    )
  }

  @Test
  fun testOneReviewedAtCapUsesSingularLowerBound() = runTestFromSetup {
    insertChain(DESCENDANT_COUNT_CAP, reviewed = 1)

    openConfirmStep()

    assertNodeWithTextExists("At least 1 of them has review history you already earned.")
  }

  @Test
  fun testNoReviewedAtCapOmitsTheHistoryLine() = runTestFromSetup {
    // Positions past the cap were never inspected, so "none" would be a guess.
    insertChain(DESCENDANT_COUNT_CAP)

    openConfirmStep()

    assertNodeWithTextExists("Remove $DESCENDANT_COUNT_CAP or more positions?")
    waitUntilNodeDoesNotExist(hasText("review history", substring = true))
  }

  /**
   * Saves the line 1. e4 e5 from the board, then steps back to e4, so e4 and its continuation e5
   * form the current branch.
   */
  private suspend fun ComposeUiTest.playIntoSavedLine() {
    playMove("e2", "e4")
    playMove("e7", "e5")
    clickOnSave()
    waitUntilSuspending { drainAllNodes(database).size == 3 }
    clickOnBack()
    assertNextMoveExist("e5")
  }

  @Test
  fun testNothingIsMarkedBeforeDelete() = runTestFromSetup {
    playIntoSavedLine()

    assertNodeWithTextExists("1. e4")

    waitUntilNodeDoesNotExist(markedForPruning)
  }

  @Test
  fun testSelectStepMarksTheCurrentTrailMove() = runTestFromSetup {
    playIntoSavedLine()

    openSelectStep()

    waitUntilNodeExists(hasText("1. e4") and markedForPruning)
  }

  @Test
  fun testSelectStepMarksTheContinuations() = runTestFromSetup {
    playIntoSavedLine()

    openSelectStep()

    waitUntilNodeExists(hasTestTag("Play e5") and markedForPruning)
  }

  @Test
  fun testConfirmStepKeepsTheBranchMarked() = runTestFromSetup {
    playIntoSavedLine()

    openConfirmStep()

    waitUntilNodeExists(hasText("1. e4") and markedForPruning)
    waitUntilNodeExists(hasTestTag("Play e5") and markedForPruning)
  }

  @Test
  fun testCancelClearsTheMarks() = runTestFromSetup {
    playIntoSavedLine()
    openSelectStep()

    assertNodeWithTagExists("pruneSelectSheetCancelButton").performClick()

    waitUntilNodeDoesNotExist(markedForPruning)
  }
}
