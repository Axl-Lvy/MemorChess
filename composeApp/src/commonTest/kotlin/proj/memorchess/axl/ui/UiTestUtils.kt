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
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import co.touchlab.kermit.Logger
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.test_util.BOARD_LOAD_TIMEOUT
import proj.memorchess.axl.test_util.ProvideTestResourceReader
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.test_util.getNextMoveDescription
import proj.memorchess.axl.test_util.getTileDescription
import proj.memorchess.axl.ui.theme.KineticTheme
import proj.memorchess.axl.ui.util.hasClickLabel

/**
 * Sets the test content and suspends until it has settled, resources included. Use it instead of
 * [ComposeUiTest.setContent], whose content may still be loading when it returns on wasmJs.
 */
suspend fun ComposeUiTest.setTestContent(content: @Composable () -> Unit) {
  setContent { ProvideTestResourceReader(content) }
  var previousTree: String? = null
  pumpUntil(TEST_TIMEOUT, "Content settled") {
    val tree = onAllNodes(isRoot(), useUnmergedTree = true).printToString()
    (tree == previousTree).also { previousTree = tree }
  }
}

/**
 * Sets the test content inside a light [KineticTheme], which needs no Koin, so the Kinetic theme
 * locals resolve in isolated tests.
 */
suspend fun ComposeUiTest.setKineticContent(content: @Composable () -> Unit) {
  setTestContent { KineticTheme(darkTheme = false) { content() } }
}

/** Clicks on the board tile [tileName], such as `e4`. */
suspend fun ComposeUiTest.clickOnTile(tileName: String) {
  waitUntilTileAppears(tileName).performClick()
}

/** Plays a move by clicking [fromTile] then [toTile]. */
suspend fun ComposeUiTest.playMove(fromTile: String, toTile: String) {
  clickOnTile(fromTile)
  clickOnTile(toTile)
}

suspend fun ComposeUiTest.promoteTo(pieceName: String) {
  waitUntilNodeExists(hasClickLabel("Promote to ${pieceName.lowercase()}")).performClick()
}

/** Clicks the button to reverse/flip the chess board orientation. */
suspend fun ComposeUiTest.clickOnReverse() {
  waitUntilNodeExists(hasContentDescription("Reverse")).assertExists().performClick()
}

/** Clicks the back button to undo the last move. */
suspend fun ComposeUiTest.clickOnBack() {
  waitUntilNodeExists(hasContentDescription("Back")).assertExists().performClick()
  awaitIdle()
}

/** Clicks the forward button to redo a previously undone move. */
suspend fun ComposeUiTest.clickOnNext() {
  waitUntilNodeExists(hasContentDescription("Forward")).assertExists().performClick()
  awaitIdle()
}

/** Clicks the reset button to return the board to its initial state. */
suspend fun ComposeUiTest.clickOnReset() {
  waitUntilNodeExists(hasContentDescription("Reset")).assertExists().performClick()
  awaitIdle()
}

/** Asserts that the tile [tileName] holds no piece. */
suspend fun ComposeUiTest.assertTileIsEmpty(tileName: String) {
  waitUntilTileAppears(tileName).assert(hasContentDescription("Piece", substring = true).not())
}

/** Asserts that the tile [tileName] holds [piece]. */
suspend fun ComposeUiTest.assertTileContainsPiece(
  tileName: String,
  piece: ChessPiece,
  timeOut: Duration = TEST_TIMEOUT,
) {
  waitUntilNodeExists(hasTestTag("Piece $piece at $tileName"), timeOut)
}

/** Asserts that [piece] moved from [fromTile] to [toTile]. */
suspend fun ComposeUiTest.assertPieceMoved(fromTile: String, toTile: String, piece: ChessPiece) {
  waitUntilTileAppears(fromTile)
  assertTileIsEmpty(fromTile)
  waitUntilTileAppears(toTile)
  assertTileContainsPiece(toTile, piece)
}

/** Asserts that [move] is among the next move suggestions and returns its node. */
suspend fun ComposeUiTest.assertNextMoveExist(move: String): SemanticsNodeInteraction {
  waitUntilNodeExists(hasTestTag(getNextMoveDescription(move)))
  return onNodeWithTag(getNextMoveDescription(move)).assertExists()
}

/** Checks if the board is reversed or not. */
suspend fun ComposeUiTest.isBoardReversed(): Boolean {
  val a1y = waitUntilTileAppears("a1").fetchSemanticsNode().positionOnScreen.y
  val a8y = waitUntilTileAppears("a8").fetchSemanticsNode().positionOnScreen.y
  return a1y < a8y
}

/** Suspends until the board of an asynchronously loading page has rendered. */
suspend fun ComposeUiTest.waitUntilBoardAppears(timeOut: Duration = BOARD_LOAD_TIMEOUT) {
  waitUntilNodeExists(hasClickLabel(getTileDescription("a1")), timeOut)
}

private suspend fun ComposeUiTest.waitUntilTileAppears(tileName: String): SemanticsNodeInteraction =
  waitUntilNodeExists(hasClickLabel(getTileDescription(tileName)))

/** Asserts that a node tagged [tag] exists and returns it. */
suspend fun ComposeUiTest.assertNodeWithTagExists(tag: String): SemanticsNodeInteraction {
  return waitUntilNodeExists(hasTestTag(tag)).assertExists()
}

/** Asserts that no node is tagged [tag]. */
fun ComposeUiTest.assertNodeWithTagDoesNotExists(tag: String) {
  onNodeWithTag(tag).assertDoesNotExist()
}

/** Asserts that a node with [text] exists and returns it. */
suspend fun ComposeUiTest.assertNodeWithTextExists(text: String): SemanticsNodeInteraction {
  return waitUntilNodeExists(hasText(text)).assertExists()
}

/** Waits until no node has [text]. */
suspend fun ComposeUiTest.assertNodeWithTextDoesNotExists(text: String) {
  waitUntilNodeDoesNotExist(hasText(text))
}

/** Slides the slider [sliderTestTag] to 80% of its width. */
suspend fun ComposeUiTest.slideToRight(sliderTestTag: String) {
  slide(sliderTestTag, 0.80f)
}

/** Slides the slider [sliderTestTag] to 10% of its width. */
suspend fun ComposeUiTest.slideToLeft(sliderTestTag: String) {
  slide(sliderTestTag, 0.10f)
}

private suspend fun ComposeUiTest.slide(sliderTestTag: String, widthFactor: Float) {
  val node = assertNodeWithTagExists(sliderTestTag)
  val width = node.fetchSemanticsNode().size.width
  LOGGER.e { "$width" }
  node.performTouchInput { click(Offset(width * widthFactor, 0f)) }
}

/**
 * Like [ComposeUiTest.waitUntil] but polls a suspending [condition], since a nested `runTest` reads
 * stale values on wasmJs, where nothing can block.
 */
suspend fun ComposeUiTest.waitUntilSuspending(
  timeOut: Duration = TEST_TIMEOUT,
  condition: suspend () -> Boolean,
) {
  pumpUntil(timeOut, "Condition", condition)
}

/** Waits until a node matches [matcher] and returns it. */
suspend fun ComposeUiTest.waitUntilNodeExists(
  matcher: SemanticsMatcher,
  timeOut: Duration = TEST_TIMEOUT,
): SemanticsNodeInteraction {
  pumpUntil(timeOut, "Node matching ${matcher.description} exists") {
    onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
  }
  return onNode(matcher).assertExists()
}

/** Waits until no node matches [matcher]. */
suspend fun ComposeUiTest.waitUntilNodeDoesNotExist(
  matcher: SemanticsMatcher,
  timeOut: Duration = TEST_TIMEOUT,
) {
  pumpUntil(timeOut, "No node matching ${matcher.description}") {
    onAllNodes(matcher).fetchSemanticsNodes().isEmpty()
  }
}

/**
 * Advances the clock frame by frame until a node matches [matcher], for tests with `autoAdvance`
 * off that await asynchronous content such as a string resource on wasmJs.
 */
suspend fun ComposeUiTest.advanceFramesUntilNodeExists(
  matcher: SemanticsMatcher,
  maxFrames: Int = 10,
): SemanticsNodeInteraction {
  repeat(maxFrames) {
    if (onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) return onNode(matcher)
    mainClock.advanceTimeByFrame()
    withContext(Dispatchers.Default) {}
  }
  return onNode(matcher).assertExists()
}

/**
 * Pumps frames until [condition] holds, since the blocking [ComposeUiTest.waitUntil] starves the
 * Compose render loop and the browser event loop on wasmJs.
 */
private suspend fun ComposeUiTest.pumpUntil(
  timeOut: Duration,
  description: String,
  condition: suspend () -> Boolean,
) {
  val mark = TimeSource.Monotonic.markNow()
  while (!condition()) {
    if (mark.elapsedNow() > timeOut) {
      throw AssertionError("$description: still not satisfied after $timeOut")
    }
    if (mainClock.autoAdvance) mainClock.advanceTimeByFrame()
    withContext(Dispatchers.Default) {}
    awaitIdle()
  }
}

/** Clicks the save button to mark the current move good. */
suspend fun ComposeUiTest.clickOnSave() {
  waitUntilNodeExists(hasContentDescription("Save", ignoreCase = true))
    .assertExists()
    .performClick()
}

private val LOGGER = Logger.withTag("ComposeUiTest")
