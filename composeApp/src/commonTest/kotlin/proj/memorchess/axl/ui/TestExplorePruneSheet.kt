package proj.memorchess.axl.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.pages.Explore

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
}
