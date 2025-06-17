package proj.akichess.axl.board

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.Rule
import proj.akichess.axl.utils.hasClickLabel
import proj.memorchess.axl.core.data.DatabaseHolder
import proj.memorchess.axl.test_util.TestDatabase
import proj.memorchess.axl.test_util.getNextMoveDescription
import proj.memorchess.axl.test_util.getTileDescription
import proj.memorchess.axl.test_util.setupConfigForTest
import proj.memorchess.axl.ui.components.control.board_control.ControllableBoard

class TestControlBar {

  @get:Rule val composeTestRule = createComposeRule()

  @OptIn(ExperimentalTestApi::class)
  @BeforeTest
  fun setUp() {
    // Use test configuration for faster tests
    setupConfigForTest()
    DatabaseHolder.init(TestDatabase.vienna())
    composeTestRule.setContent { ControllableBoard() }
    composeTestRule.waitUntilAtLeastOneExists(hasClickLabel(getTileDescription("e2")), 5_000L)
    composeTestRule.onNode(hasClickLabel(getTileDescription("e2"))).assertExists().performClick()
    composeTestRule.onNode(hasClickLabel(getTileDescription("e4"))).assertExists().performClick()
  }

  @Test
  fun testInvertBoard() {
    composeTestRule.onNodeWithTag("Reverse board").assertExists().performClick()
    composeTestRule.onNodeWithTag("Reverse board").assertExists().performClick()
  }

  @Test
  fun testBack() {
    composeTestRule.onNodeWithTag("Back").assertExists().performClick()
    composeTestRule
      .onNode(hasClickLabel(getTileDescription("e4")))
      .assert(hasContentDescription("Piece", substring = true).not())
    composeTestRule
      .onNode(hasClickLabel(getTileDescription("e2")))
      .assertExists()
      .assertContentDescriptionContains("Piece P")
    composeTestRule.onNodeWithTag(getNextMoveDescription("e4")).assertExists()
  }

  @Test
  fun testForward() {
    composeTestRule.onNodeWithTag("Back").assertExists().performClick()
    composeTestRule.onNodeWithTag("Next").assertExists().performClick()
    composeTestRule
      .onNode(hasClickLabel(getTileDescription("e4")))
      .assertExists()
      .assertContentDescriptionContains("Piece P")
    composeTestRule
      .onNode(hasClickLabel(getTileDescription("e2")))
      .assert(hasContentDescription("", substring = true).not())
    composeTestRule.onNodeWithTag(getNextMoveDescription("e4")).assertDoesNotExist()
  }

  @Test
  fun testReset() {
    composeTestRule.onNodeWithTag("Reset board").assertExists().performClick()
    composeTestRule
      .onNode(hasClickLabel(getTileDescription("e4")))
      .assert(hasContentDescription("Piece", substring = true).not())
    composeTestRule
      .onNode(hasClickLabel(getTileDescription("e2")))
      .assertExists()
      .assertContentDescriptionContains("Piece P")
  }
}
