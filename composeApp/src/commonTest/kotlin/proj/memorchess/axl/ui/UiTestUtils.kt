@file:OptIn(ExperimentalTestApi::class)

package proj.memorchess.axl.ui

import androidx.compose.runtime.Composable
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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import co.touchlab.kermit.Logger
import kotlin.time.Duration
import kotlin.time.TimeSource
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.test_util.getNextMoveDescription
import proj.memorchess.axl.test_util.getTileDescription
import proj.memorchess.axl.ui.theme.KineticTheme
import proj.memorchess.axl.ui.util.hasClickLabel

// THEME

/**
 * Sets the test content wrapped in [KineticTheme] so Composables that read the Kinetic theme locals
 * ([proj.memorchess.axl.ui.theme.LocalKineticPalette],
 * [proj.memorchess.axl.ui.theme.LocalKineticTypography]) resolve instead of crashing at their
 * `staticCompositionLocalOf` default.
 *
 * Unlike [proj.memorchess.axl.ui.theme.AppTheme], [KineticTheme] reads no config and needs no Koin,
 * so it is safe for tests that render fragments in isolation without a Koin container. A fixed
 * light theme keeps rendering deterministic across hosts.
 *
 * @param content The composable under test.
 */
fun ComposeUiTest.setKineticContent(content: @Composable () -> Unit) {
  setContent { KineticTheme(darkTheme = false) { content() } }
}

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
  waitUntilNodeExists(hasContentDescription("Reverse")).assertExists().performClick()
}

/** Clicks the back button to undo the last move. */
fun ComposeUiTest.clickOnBack() {
  waitUntilNodeExists(hasContentDescription("Back")).assertExists().performClick()
  waitForIdle()
}

/** Clicks the forward button to redo a previously undone move. */
fun ComposeUiTest.clickOnNext() {
  waitUntilNodeExists(hasContentDescription("Forward")).assertExists().performClick()
  waitForIdle()
}

/** Clicks the reset button to return the board to its initial state. */
fun ComposeUiTest.clickOnReset() {
  waitUntilNodeExists(hasContentDescription("Reset")).assertExists().performClick()
  waitForIdle()
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
 * @param timeOut The maximum time to wait for the tile to appear
 */
fun ComposeUiTest.assertTileContainsPiece(
  tileName: String,
  piece: ChessPiece,
  timeOut: Duration = TEST_TIMEOUT,
) {
  waitUntilNodeExists(hasTestTag("Piece $piece at $tileName"), timeOut)
}

/**
 * Asserts that a piece has moved from one tile to another. This checks that the source tile is
 * empty and the destination tile contains the specified piece.
 *
 * @param fromTile The algebraic notation of the source tile (e.g., "e2")
 * @param toTile The algebraic notation of the destination tile (e.g., "e4")
 * @param piece The chess piece that should have moved
 */
fun ComposeUiTest.assertPieceMoved(fromTile: String, toTile: String, piece: ChessPiece) {
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
  waitUntilDoesNotExist(hasText(text), TEST_TIMEOUT.inWholeMilliseconds)
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
  LOGGER.e { "$width" }
  node.performTouchInput { click(Offset(width * widthFactor, 0f)) }
}

/**
 * Like [ComposeUiTest.waitUntil] but accepts a suspending condition.
 *
 * [ComposeUiTest.waitUntil] takes a plain lambda, which forces conditions that need suspend APIs
 * (such as the database) to spin up a nested `runTest`. That cannot work on wasmJs where nothing
 * can block: the nested test body runs asynchronously and the condition reads a stale value. Tests
 * running inside the suspending `runComposeUiTest` block call this instead and poll the suspend API
 * directly, pumping composition between polls.
 *
 * @param timeOut The maximum time to wait for the condition to become true
 * @param condition The suspending condition to poll
 */
suspend fun ComposeUiTest.waitUntilSuspending(
  timeOut: Duration = TEST_TIMEOUT,
  condition: suspend () -> Boolean,
) {
  val mark = TimeSource.Monotonic.markNow()
  while (!condition()) {
    if (mark.elapsedNow() > timeOut) {
      throw AssertionError("Condition still not satisfied after $timeOut")
    }
    waitForIdle()
  }
}

/**
 * Waits until a UI element with the specified matcher exists and returns it.
 *
 * @param matcher The matcher used to identify the UI element
 * @param timeOut The maximum time to wait for the element to appear
 * @return The SemanticsNodeInteraction representing the found element
 */
fun ComposeUiTest.waitUntilNodeExists(
  matcher: SemanticsMatcher,
  timeOut: Duration = TEST_TIMEOUT,
): SemanticsNodeInteraction {
  waitUntilAtLeastOneExists(matcher, timeOut.inWholeMilliseconds)
  return onNode(matcher).assertExists()
}

// DATABASE

/** Clicks the "Save Good" button to mark the current position as a good move. */
fun ComposeUiTest.clickOnSave() {
  waitUntilNodeExists(hasContentDescription("Save", ignoreCase = true))
    .assertExists()
    .performClick()
}

private val LOGGER = Logger.withTag("ComposeUiTest")
