package proj.memorchess.axl

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.data.DatabaseHolder
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.engine.pieces.IPiece
import proj.memorchess.axl.test_util.getNavigationButtonDescription
import proj.memorchess.axl.test_util.getNextMoveDescription
import proj.memorchess.axl.test_util.getTileDescription
import proj.memorchess.axl.ui.pages.navigation.Destination
import proj.memorchess.axl.utils.hasClickLabel

@OptIn(ExperimentalTestApi::class)
abstract class AUiTestFactory {

  lateinit var composeTestRule:
    AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

  abstract fun createTests(): List<() -> Unit>

  abstract fun beforeEach()

  open fun needsDatabaseReset(): Boolean = false

  // NAVIGATION

  fun goToExplore() {
    clickOnDestinationButton(Destination.EXPLORE)
    assertNodeWithTagExists(Destination.EXPLORE.name)
  }

  fun goToTraining() {
    clickOnDestinationButton(Destination.TRAINING)
    assertNodeWithTagExists(Destination.TRAINING.name)
  }

  fun goToSettings() {
    clickOnDestinationButton(Destination.SETTINGS)
    assertNodeWithTagExists(Destination.SETTINGS.name)
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

  // BOARD

  fun clickOnTile(tileName: String) {
    val matcher = hasClickLabel(getTileDescription(tileName))
    composeTestRule.onNode(matcher).assertExists().performClick()
  }

  fun playMove(fromTile: String, toTile: String) {
    clickOnTile(fromTile)
    clickOnTile(toTile)
  }

  fun clickOnReverse() {
    composeTestRule.onNodeWithTag("Reverse board").assertExists().performClick()
  }

  fun clickOnBack() {
    composeTestRule.onNodeWithTag("Back").assertExists().performClick()
  }

  fun clickOnNext() {
    composeTestRule.onNodeWithTag("Next").assertExists().performClick()
  }

  fun clickOnReset() {
    composeTestRule.onNodeWithTag("Reset board").assertExists().performClick()
  }

  fun assertTileIsEmpty(tileName: String) {
    composeTestRule
      .onNode(hasClickLabel(getTileDescription(tileName)))
      .assertExists()
      .assert(hasContentDescription("Piece", substring = true).not())
  }

  fun assertTileContainsPiece(tileName: String, piece: IPiece) {
    composeTestRule
      .onNode(hasClickLabel(getTileDescription(tileName)))
      .assertExists()
      .assertContentDescriptionContains("Piece $piece")
  }

  fun assertPieceMoved(fromTile: String, toTile: String, piece: IPiece) {
    assertTileIsEmpty(fromTile)
    assertTileContainsPiece(toTile, piece)
  }

  fun assertNextMoveExist(move: String): SemanticsNodeInteraction {
    return composeTestRule.onNodeWithTag(getNextMoveDescription(move)).assertExists()
  }

  // GENERAL

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

  // DATABASE

  fun getAllPositions(): List<StoredNode> {
    lateinit var allPositions: List<StoredNode>
    runTest { allPositions = DatabaseHolder.getDatabase().getAllPositions() }
    return allPositions
  }

  fun getPosition(positionKey: PositionKey): StoredNode? {
    var position: StoredNode? = null
    runTest { position = DatabaseHolder.getDatabase().getPosition(positionKey) }
    return position
  }

  fun clickOnSaveGood() {
    composeTestRule.onNodeWithContentDescription("Save Good").assertExists().performClick()
  }

  fun clickOnSaveBad() {
    composeTestRule.onNodeWithContentDescription("Save Bad").assertExists().performClick()
  }
}
