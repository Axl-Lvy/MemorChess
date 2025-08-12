package proj.memorchess.axl

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import org.junit.Rule
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.test_util.getTileDescription
import proj.memorchess.axl.ui.pages.navigation.Destination
import proj.memorchess.axl.ui.util.hasClickLabel

@OptIn(ExperimentalTestApi::class)
class TestNavigation {
  /**
   * The Compose test rule used to interact with the UI. This is automatically initialized by JUnit
   * before tests are executed.
   */
  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun testGoToExplore() {
    this.clickOnDestinationButton(Destination.EXPLORE)
    composeTestRule.waitUntilAtLeastOneExists(
      hasClickLabel(getTileDescription("e2")),
      TEST_TIMEOUT.inWholeMilliseconds,
    )
    assertNodeWithTagExists("bottom_navigation_bar_item_${Destination.EXPLORE.label}")
  }

  @Test
  fun testGoToTraining() {
    this.clickOnDestinationButton(Destination.TRAINING)
    composeTestRule.waitUntilAtLeastOneExists(
      hasClickLabel(getTileDescription("e2")).or(hasText("Bravo !")),
      TEST_TIMEOUT.inWholeMilliseconds,
    )
    assertNodeWithTagExists("bottom_navigation_bar_item_${Destination.TRAINING.label}")
  }

  @Test
  fun testGoToSettings() {
    this.clickOnDestinationButton(Destination.SETTINGS)
    assertNodeWithTagExists("bottom_navigation_bar_item_${Destination.SETTINGS.label}")
  }

  /**
   * Clicks on a navigation button for the specified destination.
   *
   * This method waits for the navigation button to be visible, then clicks on it.
   *
   * @param destination The destination to navigate to
   */
  private fun clickOnDestinationButton(destination: Destination) {
    waitUntilNodeExists(hasTestTag("bottom_navigation_bar_item_${destination.label}"))
      .performClick()
  }

  /**
   * Asserts that a UI element with the specified tag exists and returns it.
   *
   * @param tag The tag used to identify the UI element
   * @return The SemanticsNodeInteraction representing the found element
   * @throws AssertionError if no element with the specified tag exists
   */
  fun assertNodeWithTagExists(tag: String): SemanticsNodeInteraction {
    return waitUntilNodeExists(hasTestTag(tag)).assertExists()
  }

  fun waitUntilNodeExists(matcher: SemanticsMatcher): SemanticsNodeInteraction {
    composeTestRule.waitUntilAtLeastOneExists(matcher, TEST_TIMEOUT.inWholeMilliseconds)
    return composeTestRule.onNode(matcher).assertExists()
  }
}
