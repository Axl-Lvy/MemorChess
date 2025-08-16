@file:OptIn(ExperimentalTestApi::class)

package proj.memorchess.axl.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import com.diamondedge.logging.logging
import kotlinx.coroutines.test.runTest
import proj.memorchess.axl.core.engine.pieces.Piece
import proj.memorchess.axl.test_util.Awaitility
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.test_util.getNextMoveDescription
import proj.memorchess.axl.test_util.getTileDescription
import proj.memorchess.axl.ui.util.hasClickLabel

// BOARD

/**
 * Clicks on a chess board tile with the specified name.
 *
 * @param tileName The algebraic notation of the tile (e.g., "e4", "h8")
 */
fun ComposeUiTest.clickOnTile(tileName: String) {
  waitUntilTileAppears(tileName).performClick()
}

/**
 * Plays a chess move by clicking on the source tile and then the destination tile.
 *
 * @param fromTile The algebraic notation of the source tile (e.g., "e2")
 * @param toTile The algebraic notation of the destination tile (e.g., "e4")
 */
fun ComposeUiTest.playMove(fromTile: String, toTile: String) {
  clickOnTile(fromTile)
  clickOnTile(toTile)
}

fun ComposeUiTest.promoteTo(pieceName: String) {
  waitUntilNodeExists(hasClickLabel("Promote to ${pieceName.lowercase()}")).performClick()
}

/** Clicks the button to reverse/flip the chess board orientation. */
fun ComposeUiTest.clickOnReverse() {
  waitUntilNodeExists(hasTestTag("Reverse board")).assertExists().performClick()
}

/** Clicks the back button to undo the last move. */
fun ComposeUiTest.clickOnBack() {
  waitUntilNodeExists(hasTestTag("Back")).assertExists().performClick()
  runTest { awaitIdle() }
}

/** Clicks the next button to redo a previously undone move. */
fun ComposeUiTest.clickOnNext() {
  waitUntilNodeExists(hasTestTag("Next")).assertExists().performClick()
  runTest { awaitIdle() }
}

/** Clicks the reset button to return the board to its initial state. */
fun ComposeUiTest.clickOnReset() {
  waitUntilNodeExists(hasTestTag("Reset board")).assertExists().performClick()
  runTest { awaitIdle() }
}

/**
 * Asserts that a specific tile on the chess board is empty (contains no piece).
 *
 * @param tileName The algebraic notation of the tile to check (e.g., "e4")
 */
fun ComposeUiTest.assertTileIsEmpty(tileName: String) {
  waitUntilTileAppears(tileName).assert(hasContentDescription("Piece", substring = true).not())
}

/**
 * Asserts that a specific tile on the chess board contains the specified piece.
 *
 * @param tileName The algebraic notation of the tile to check (e.g., "e4")
 * @param piece The chess piece that should be on the tile
 */
fun ComposeUiTest.assertTileContainsPiece(tileName: String, piece: Piece) {
  waitUntilNodeExists(hasTestTag("Piece $piece at $tileName"))
}

/**
 * Asserts that a piece has moved from one tile to another. This checks that the source tile is
 * empty and the destination tile contains the specified piece.
 *
 * @param fromTile The algebraic notation of the source tile (e.g., "e2")
 * @param toTile The algebraic notation of the destination tile (e.g., "e4")
 * @param piece The chess piece that should have moved
 */
fun ComposeUiTest.assertPieceMoved(fromTile: String, toTile: String, piece: Piece) {
  waitUntilTileAppears(fromTile)
  assertTileIsEmpty(fromTile)
  waitUntilTileAppears(toTile)
  assertTileContainsPiece(toTile, piece)
}

/**
 * Asserts that a specific move exists in the next move suggestions and returns the node.
 *
 * @param move The algebraic notation of the move (e.g., "e4")
 * @return The SemanticsNodeInteraction representing the move suggestion
 */
fun ComposeUiTest.assertNextMoveExist(move: String): SemanticsNodeInteraction {
  waitUntilNodeExists(hasTestTag(getNextMoveDescription(move)))
  return onNodeWithTag(getNextMoveDescription(move)).assertExists()
}

/** Checks if the board is reversed or not. */
fun ComposeUiTest.isBoardReversed(): Boolean {
  val a1y = waitUntilTileAppears("a1").fetchSemanticsNode().positionOnScreen.y
  val a8y = waitUntilTileAppears("a8").fetchSemanticsNode().positionOnScreen.y
  return a1y < a8y
}

private fun ComposeUiTest.waitUntilTileAppears(tileName: String): SemanticsNodeInteraction {
  val matcher = hasClickLabel(getTileDescription(tileName))
  waitUntilAtLeastOneExists(matcher, TEST_TIMEOUT.inWholeMilliseconds)
  return onNode(matcher).assertExists()
}

// GENERAL

/**
 * Asserts that a UI element with the specified tag exists and returns it.
 *
 * @param tag The tag used to identify the UI element
 * @return The SemanticsNodeInteraction representing the found element
 * @throws AssertionError if no element with the specified tag exists
 */
fun ComposeUiTest.assertNodeWithTagExists(tag: String): SemanticsNodeInteraction {
  return waitUntilNodeExists(hasTestTag(tag)).assertExists()
}

/**
 * Asserts that no UI element with the specified tag exists.
 *
 * @param tag The tag that should not be present in the UI
 * @throws AssertionError if an element with the specified tag exists
 */
fun ComposeUiTest.assertNodeWithTagDoesNotExists(tag: String) {
  onNodeWithTag(tag).assertDoesNotExist()
}

/**
 * Asserts that a UI element with the specified text exists and returns it.
 *
 * @param text The text to search for in the UI
 * @return The SemanticsNodeInteraction representing the found element
 * @throws AssertionError if no element with the specified text exists
 */
fun ComposeUiTest.assertNodeWithTextExists(text: String): SemanticsNodeInteraction {
  return waitUntilNodeExists(hasText(text)).assertExists()
}

/**
 * Asserts that no UI element with the specified text exists.
 *
 * @param text The text that should not be present in the UI
 * @throws AssertionError if an element with the specified text exists
 */
fun ComposeUiTest.assertNodeWithTextDoesNotExists(text: String) {
  Awaitility.awaitUntilTrue(TEST_TIMEOUT, failingMessage = "Node with text $text exists") {
    try {
      onNodeWithText(text).assertDoesNotExist()
      return@awaitUntilTrue true
    } catch (e: AssertionError) {
      return@awaitUntilTrue false
    }
  }
}

/**
 * Slides a slider far to right. Don't reaches the end, but almost.
 *
 * @param sliderTestTag The tag of the slider to slide
 */
fun ComposeUiTest.slideToRight(sliderTestTag: String) {
  slide(sliderTestTag, 0.80f)
}

/**
 * Slides a slider far to left. Don't reaches the end, but almost.
 *
 * @param sliderTestTag The tag of the slider to slide
 */
fun ComposeUiTest.slideToLeft(sliderTestTag: String) {
  slide(sliderTestTag, 0.10f)
}

private fun ComposeUiTest.slide(sliderTestTag: String, widthFactor: Float) {
  val node = assertNodeWithTagExists(sliderTestTag)
  val width = node.fetchSemanticsNode().size.width
  LOGGER.error { width }
  node.performTouchInput { click(Offset(width * widthFactor, 0f)) }
}

fun ComposeUiTest.waitUntilNodeExists(matcher: SemanticsMatcher): SemanticsNodeInteraction {
  waitUntilAtLeastOneExists(matcher, TEST_TIMEOUT.inWholeMilliseconds)
  return onNode(matcher).assertExists()
}

// DATABASE

/** Clicks the "Save Good" button to mark the current position as a good move. */
fun ComposeUiTest.clickOnSave() {
  waitUntilNodeExists(hasContentDescription("Save", ignoreCase = true))
    .assertExists()
    .performClick()
}

private val LOGGER = logging()
