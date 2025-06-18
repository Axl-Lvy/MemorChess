package proj.memorchess.axl.board

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.Rule
import proj.memorchess.axl.utils.hasClickLabel
import proj.memorchess.axl.core.data.DatabaseHolder
import proj.memorchess.axl.test_util.TestDatabase
import proj.memorchess.axl.test_util.getNextMoveDescription
import proj.memorchess.axl.test_util.getTileDescription
import proj.memorchess.axl.ui.components.control.board_control.ControllableBoard

class TestNextMoveBar {

  @get:Rule val composeTestRule = createComposeRule()

  @BeforeTest
  fun setUp() {
    DatabaseHolder.init(TestDatabase.vienna())
    composeTestRule.setContent { ControllableBoard() }
    composeTestRule.onNode(hasClickLabel(getTileDescription("e2"))).assertExists().performClick()
    composeTestRule.onNode(hasClickLabel(getTileDescription("e4"))).assertExists().performClick()
  }

  @Test
  fun testNextMoveAppears() {
    composeTestRule.onNodeWithTag("Back").assertExists().performClick()
    composeTestRule.onNodeWithTag(getNextMoveDescription("e4")).assertExists()
  }

  @Test
  fun testNextMoveWorks() {
    composeTestRule.onNodeWithTag("Back").assertExists().performClick()
    composeTestRule.onNodeWithTag(getNextMoveDescription("e4")).assertExists().performClick()
    composeTestRule
      .onNode(hasClickLabel(getTileDescription("e4")))
      .assertExists()
      .assertContentDescriptionContains("Piece P")
    composeTestRule
      .onNode(hasClickLabel(getTileDescription("e2")))
      .assert(hasContentDescription("", substring = true).not())
  }

  @Test
  fun testMultipleNextMoves() {
    composeTestRule.onNodeWithTag("Back").assertExists().performClick()
    composeTestRule.onNode(hasClickLabel(getTileDescription("e2"))).assertExists().performClick()
    composeTestRule.onNode(hasClickLabel(getTileDescription("e3"))).assertExists().performClick()
    composeTestRule.onNodeWithTag("Back").assertExists().performClick()

    composeTestRule.onNodeWithTag(getNextMoveDescription("e4")).assertExists()
    composeTestRule.onNodeWithTag(getNextMoveDescription("e3")).assertExists().performClick()
    composeTestRule
      .onNode(hasClickLabel(getTileDescription("e3")))
      .assertExists()
      .assertContentDescriptionContains("Piece P")
    composeTestRule
      .onNode(hasClickLabel(getTileDescription("e2")))
      .assert(hasContentDescription("", substring = true).not())
    composeTestRule
      .onNode(hasClickLabel(getTileDescription("e4")))
      .assert(hasContentDescription("", substring = true).not())
  }

  @Test
  fun testNextMoveAfterReset() {
    composeTestRule.onNodeWithTag("Reset board").assertExists().performClick()
    composeTestRule.onNodeWithTag(getNextMoveDescription("e4")).assertExists().performClick()
    composeTestRule
      .onNode(hasClickLabel(getTileDescription("e4")))
      .assertExists()
      .assertContentDescriptionContains("Piece P")
    composeTestRule
      .onNode(hasClickLabel(getTileDescription("e2")))
      .assert(hasContentDescription("", substring = true).not())
  }
}
