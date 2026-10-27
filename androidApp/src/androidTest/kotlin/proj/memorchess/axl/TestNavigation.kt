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
import proj.memorchess.axl.test_util.hasClickLabel
import proj.memorchess.axl.ui.pages.navigation.Route

@OptIn(ExperimentalTestApi::class)
class TestNavigation {
  /** Compose rule driving [MainActivity]. */
  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun testGoToExplore() {
    this.clickOnDestinationButton(Route.ExploreRoute.DEFAULT)
    composeTestRule.waitUntilAtLeastOneExists(
      hasClickLabel(getTileDescription("e2")),
      TEST_TIMEOUT.inWholeMilliseconds,
    )
    assertNodeWithTagExists("bottom_navigation_bar_item_${Route.ExploreRoute.DEFAULT.getLabel()}")
  }

  @Test
  fun testGoToTraining() {
    // Today is the start destination, so tapping Training from there would be a no-op.
    this.clickOnDestinationButton(Route.ExploreRoute.DEFAULT)
    this.clickOnDestinationButton(Route.TodayRoute)
    assertNodeWithTagExists("bottom_navigation_bar_item_${Route.TodayRoute.getLabel()}")
    waitUntilNodeExists(hasTestTag("today_cta")).performClick()

    composeTestRule.waitUntilAtLeastOneExists(
      hasClickLabel(getTileDescription("e2")).or(hasText("Bravo !")),
      TEST_TIMEOUT.inWholeMilliseconds,
    )
  }

  @Test
  fun testGoToSettings() {
    this.clickOnDestinationButton(Route.SettingsRoute)
    assertNodeWithTagExists("bottom_navigation_bar_item_${Route.SettingsRoute.getLabel()}")
  }

  private fun clickOnDestinationButton(destination: Route) {
    waitUntilNodeExists(hasTestTag("bottom_navigation_bar_item_${destination.getLabel()}"))
      .performClick()
  }

  /** Waits for the node tagged [tag] and asserts it exists. */
  fun assertNodeWithTagExists(tag: String): SemanticsNodeInteraction {
    return waitUntilNodeExists(hasTestTag(tag)).assertExists()
  }

  fun waitUntilNodeExists(matcher: SemanticsMatcher): SemanticsNodeInteraction {
    composeTestRule.waitUntilAtLeastOneExists(matcher, TEST_TIMEOUT.inWholeMilliseconds)
    return composeTestRule.onNode(matcher).assertExists()
  }
}
