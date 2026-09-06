package proj.memorchess.axl.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DESCENDANT_COUNT_CAP
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.pages.Explore
import proj.memorchess.axl.ui.util.hasClickLabel

@OptIn(ExperimentalTestApi::class)
class TestExplorePruneSheet : TestWithKoin() {

  private val database: DatabaseQueryManager by inject()

  private fun runTestFromSetup(block: suspend ComposeUiTest.() -> Unit) = runComposeUiTest {
    koinSetUp()
    try {
      setContent { InitializeApp { Explore() } }
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
   * exactly one child, so `countDescendants` from the root returns exactly [length].
   */
  private fun buildDescendantChain(length: Int): List<DataNode> =
    (0 until length).map { index ->
      val incoming = if (index == 0) emptyList() else listOf(chainMove(index - 1, index))
      val outgoing = if (index == length - 1) emptyList() else listOf(chainMove(index, index + 1))
      DataNode(chainKey(index), PreviousAndNextMoves(incoming, outgoing), CardStateFactory.new())
    }

  @Test
  fun testDeleteButtonShowsPruneSheetWithCount() = runTestFromSetup {
    database.insertNodes(
      DataNode(PositionKey.START_POSITION, PreviousAndNextMoves(), CardStateFactory.new())
    )
    waitUntilNodeExists(hasContentDescription("Delete")).performClick()
    assertNodeWithTagExists("pruneConfirmSheet")
    assertNodeWithTextExists("Are you sure you want to delete 1 position?")
  }

  @Test
  fun testCancelDismissesSheetWithoutDeleting() = runTestFromSetup {
    database.insertNodes(
      DataNode(PositionKey.START_POSITION, PreviousAndNextMoves(), CardStateFactory.new())
    )
    waitUntilNodeExists(hasContentDescription("Delete")).performClick()
    assertNodeWithTagExists("pruneConfirmSheetCancelButton").performClick()
    waitUntilDoesNotExist(hasTestTag("pruneConfirmSheet"), TEST_TIMEOUT.inWholeMilliseconds)
    assertNotNull(database.getPosition(PositionKey.START_POSITION))
  }

  @Test
  fun testConfirmDeletesTheCurrentNode() = runTestFromSetup {
    database.insertNodes(
      DataNode(PositionKey.START_POSITION, PreviousAndNextMoves(), CardStateFactory.new())
    )
    waitUntilNodeExists(hasContentDescription("Delete")).performClick()
    assertNodeWithTagExists("pruneConfirmSheetOkButton").performClick()
    waitUntilSuspending { database.getPosition(PositionKey.START_POSITION) == null }
  }

  @Test
  fun testTappingSheetBodyDoesNotDismissSheet() = runTestFromSetup {
    // Regression test: the panel used to have no pointer-input node of its own, so a tap landing
    // on its text or padding fell through to the full-screen scrim behind it and dismissed the
    // sheet instead of doing nothing.
    database.insertNodes(
      DataNode(PositionKey.START_POSITION, PreviousAndNextMoves(), CardStateFactory.new())
    )
    waitUntilNodeExists(hasContentDescription("Delete")).performClick()
    assertNodeWithTextExists("Are you sure you want to delete 1 position?").performClick()
    assertNodeWithTagExists("pruneConfirmSheet")
  }

  @Test
  fun testTappingScrimDismissesSheetWithoutDeleting() = runTestFromSetup {
    database.insertNodes(
      DataNode(PositionKey.START_POSITION, PreviousAndNextMoves(), CardStateFactory.new())
    )
    waitUntilNodeExists(hasContentDescription("Delete")).performClick()
    assertNodeWithTagExists("pruneConfirmSheet")
    // Top-left corner of the full-screen scrim: always clear of the bottom-anchored panel.
    waitUntilNodeExists(hasClickLabel("Close")).performTouchInput { click(Offset(1f, 1f)) }
    waitUntilDoesNotExist(hasTestTag("pruneConfirmSheet"), TEST_TIMEOUT.inWholeMilliseconds)
    assertNotNull(database.getPosition(PositionKey.START_POSITION))
  }

  @Test
  fun testZeroDescendantsShowsZeroCountWithLiveDeleteButton() = runTestFromSetup {
    // No node saved at the current position: countDescendants returns 0, and the plural "other"
    // arm (not "one") reads "delete 0 positions?". The Ok button must still work.
    waitUntilNodeExists(hasContentDescription("Delete")).performClick()
    assertNodeWithTextExists("Are you sure you want to delete 0 positions?")
    assertNodeWithTagExists("pruneConfirmSheetOkButton").assertIsEnabled().performClick()
    waitUntilDoesNotExist(hasTestTag("pruneConfirmSheet"), TEST_TIMEOUT.inWholeMilliseconds)
    assertNull(database.getPosition(PositionKey.START_POSITION))
  }

  @Test
  fun testTwoDescendantsUsesPluralOtherText() = runTestFromSetup {
    database.insertNodes(*buildDescendantChain(2).toTypedArray())
    waitUntilNodeExists(hasContentDescription("Delete")).performClick()
    assertNodeWithTextExists("Are you sure you want to delete 2 positions?")
  }

  @Test
  fun testDescendantCountJustBelowCapShowsPluralText() = runTestFromSetup {
    database.insertNodes(*buildDescendantChain(DESCENDANT_COUNT_CAP - 1).toTypedArray())
    waitUntilNodeExists(hasContentDescription("Delete")).performClick()
    assertNodeWithTextExists(
      "Are you sure you want to delete ${DESCENDANT_COUNT_CAP - 1} positions?"
    )
  }

  @Test
  fun testDescendantCountAtCapShowsCappedText() = runTestFromSetup {
    database.insertNodes(*buildDescendantChain(DESCENDANT_COUNT_CAP).toTypedArray())
    waitUntilNodeExists(hasContentDescription("Delete")).performClick()
    assertNodeWithTextExists(
      "Are you sure you want to delete $DESCENDANT_COUNT_CAP or more positions?"
    )
  }
}
