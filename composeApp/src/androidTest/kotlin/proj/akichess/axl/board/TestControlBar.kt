package proj.akichess.axl.board

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
import proj.ankichess.axl.core.data.DatabaseHolder
import proj.ankichess.axl.test_util.TestDataBase
import proj.ankichess.axl.test_util.getNextMoveDescription
import proj.ankichess.axl.test_util.getTileDescription
import proj.ankichess.axl.ui.components.control.board_control.ControllableBoardPage

class TestControlBar {

  @get:Rule val composeTestRule = createComposeRule()

  @BeforeTest
  fun setUp() {
    DatabaseHolder.init(TestDataBase.vienna())
    composeTestRule.setContent { ControllableBoardPage() }
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
