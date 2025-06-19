package proj.memorchess.axl

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.DatabaseHolder
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.test_util.getNavigationButtonDescription
import proj.memorchess.axl.ui.pages.navigation.Destination

@OptIn(ExperimentalTestApi::class)
abstract class AUiTestFactory {

  lateinit var composeTestRule:
    AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

  abstract fun createTests(): List<() -> Unit>

  abstract fun beforeEach()

  fun clickOnExplore() {
    clickOnDestinationButton(Destination.EXPLORE)
  }

  fun clickOnTraining() {
    clickOnDestinationButton(Destination.TRAINING)
  }

  fun clickOnSettings() {
    clickOnDestinationButton(Destination.SETTINGS)
  }

  private fun clickOnDestinationButton(destination: Destination) {
    composeTestRule.waitUntilAtLeastOneExists(
      hasContentDescription(getNavigationButtonDescription(destination.name))
    )
    composeTestRule
      .onNodeWithContentDescription(getNavigationButtonDescription(destination.name))
      .assertExists()
      .performClick()
  }

  fun assertNodeWithTagExists(tag: String): SemanticsNodeInteraction {
    return composeTestRule.onNodeWithTag(tag).assertExists()
  }

  fun assertNodeWithTagDoesNotExists(tag: String) {
    composeTestRule.onNodeWithTag(tag).assertDoesNotExist()
  }

  fun assertNodeWithTextExists(text: String): SemanticsNodeInteraction {
    return composeTestRule.onNodeWithText(text).assertExists()
  }

  fun assertNodeWithTextDoesNotExists(text: String) {
    composeTestRule.onNodeWithText(text).assertDoesNotExist()
  }

  fun getAllPosition(): List<StoredNode> {
    lateinit var allPositions: List<StoredNode>
    runTest { allPositions = DatabaseHolder.getDatabase().getAllPositions() }
    return allPositions
  }
}
