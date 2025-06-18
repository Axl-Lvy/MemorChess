package proj.memorchess.axl.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlin.test.BeforeTest
import org.junit.Rule
import org.junit.Test
import proj.memorchess.axl.App
import proj.memorchess.axl.core.data.DatabaseHolder
import proj.memorchess.axl.test_util.TestDatabase
import proj.memorchess.axl.test_util.getNavigationButtonDescription
import proj.memorchess.axl.ui.pages.navigation.Destination

class TestPageNavigation {

  @get:Rule val composeTestRule = createComposeRule()

  @BeforeTest
  fun setUp() {
    DatabaseHolder.init(TestDatabase.vienna())
    composeTestRule.setContent { App() }
  }

  @Test
  fun testNavigateToPage() {
    composeTestRule
      .onNodeWithContentDescription(getNavigationButtonDescription(Destination.EXPLORE.name))
      .assertExists()
      .performClick()
    composeTestRule.onNodeWithTag(Destination.EXPLORE.name).assertExists()
    composeTestRule
      .onNodeWithContentDescription(getNavigationButtonDescription(Destination.TRAINING.name))
      .assertExists()
      .performClick()
    composeTestRule.onNodeWithTag(Destination.TRAINING.name).assertExists()
    composeTestRule
      .onNodeWithContentDescription(getNavigationButtonDescription(Destination.SETTINGS.name))
      .assertExists()
      .performClick()
    composeTestRule.onNodeWithTag(Destination.SETTINGS.name).assertExists()
  }
}
