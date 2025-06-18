package proj.memorchess.axl

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import proj.memorchess.axl.test_util.getNavigationButtonDescription
import proj.memorchess.axl.ui.pages.navigation.Destination

@OptIn(ExperimentalTestApi::class)
abstract class AUiTestFactory {

  lateinit var composeTestRule:
    AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

  abstract fun createTests(): List<() -> Unit>

  fun clickOnExplore() {
    clickOnDestinationButton(Destination.EXPLORE)
  }

  fun clickOnTraining() {
    clickOnDestinationButton(Destination.TRAINING)
  }

  fun clickOnSettings() {
    clickOnDestinationButton(Destination.SETTINGS)
  }

  fun assertNodeWithTagExists(tag: String) {
    composeTestRule.onNodeWithTag(tag).assertExists()
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
}
