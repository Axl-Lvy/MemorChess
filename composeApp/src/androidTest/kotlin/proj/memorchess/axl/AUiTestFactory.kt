package proj.memorchess.axl

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
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

/**
 * Abstract base class for UI test factories.
 *
 * This class provides a structured approach to creating UI tests by:
 * 1. Organizing tests into logical groups (factories)
 * 2. Providing common utility methods for UI interactions
 * 3. Standardizing test setup and execution
 *
 * Test factories are designed to work with the [TestRunner], which manages the lifecycle of test
 * execution and provides a single application instance for all tests to improve performance.
 *
 * To create a new test factory:
 * 1. Extend this class
 * 2. Implement [createTests] to return a list of test functions
 * 3. Implement [beforeEach] to set up the test environment
 * 4. Override [needsDatabaseReset] if your tests require a fresh database
 * 5. Add your test factory to the [TestRunner.testFactories] list
 *
 * Example:
 * ```
 * class MyTestFactory : AUiTestFactory() {
 *   override fun createTests(): List<() -> Unit> {
 *     return listOf(::testFeatureA, ::testFeatureB)
 *   }
 *
 *   override fun beforeEach() {
 *     goToExplore() // Navigate to the explore screen before each test
 *   }
 *
 *   private fun testFeatureA() {
 *     // Test implementation
 *   }
 *
 *   private fun testFeatureB() {
 *     // Test implementation
 *   }
 * }
 * ```
 *
 * This class contains a lot of ready-to-use methods for common UI interactions, do not hesitate to
 * use them and to create new one if needed. Tests are way more readable this way.
 */
@OptIn(ExperimentalTestApi::class)
abstract class AUiTestFactory {

  /**
   * The Compose test rule used to interact with the UI. This is set by the [TestRunner] before
   * tests are executed.
   *
   * Always prefer using built-in methods rather than accessing this property directly.
   */
  lateinit var composeTestRule:
    AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

  /**
   * Creates a list of test functions to be executed by the [TestRunner].
   *
   * Each function in the list represents a single test case. Tests will be executed in the order
   * they appear in the list.
   *
   * @return A list of test functions
   */
  abstract fun createTests(): List<() -> Unit>

  /**
   * Sets up the test environment before each test is executed.
   *
   * This method is called by the [TestRunner] before each test function from [createTests] is
   * executed. Use this method to:
   * - Navigate to the appropriate screen
   * - Set up initial state
   * - Prepare test data
   */
  abstract fun beforeEach()

  /**
   * Indicates whether the database should be reset before running tests from this factory.
   *
   * Override this method and return true if your tests require a fresh database. By default, the
   * database is not reset to improve test execution speed.
   *
   * @return true if the database should be reset, false otherwise
   */
  open fun needsDatabaseReset(): Boolean = false

  // NAVIGATION

  /**
   * Navigates to the Explore screen.
   *
   * This method clicks on the Explore navigation button and verifies that the Explore screen is
   * displayed before returning.
   */
  fun goToExplore() {
    clickOnDestinationButton(Destination.EXPLORE)
    composeTestRule.waitUntilAtLeastOneExists(hasClickLabel(getTileDescription("e2")))
    assertNodeWithTagExists(Destination.EXPLORE.name)
  }

  /**
   * Navigates to the Training screen.
   *
   * This method clicks on the Training navigation button and verifies that the Training screen is
   * displayed before returning.
   */
  fun goToTraining() {
    clickOnDestinationButton(Destination.TRAINING)
    composeTestRule.waitUntilAtLeastOneExists(
      hasClickLabel(getTileDescription("e2")).or(hasText("Bravo !"))
    )
    assertNodeWithTagExists(Destination.TRAINING.name)
  }

  /**
   * Navigates to the Settings screen.
   *
   * This method clicks on the Settings navigation button and verifies that the Settings screen is
   * displayed before returning.
   */
  fun goToSettings() {
    clickOnDestinationButton(Destination.SETTINGS)
    assertNodeWithTagExists(Destination.SETTINGS.name)
  }

  /**
   * Clicks on a navigation button for the specified destination.
   *
   * This method waits for the navigation button to be visible, then clicks on it.
   *
   * @param destination The destination to navigate to
   */
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

  /**
   * Clicks on a chess board tile with the specified name.
   *
   * @param tileName The algebraic notation of the tile (e.g., "e4", "h8")
   */
  fun clickOnTile(tileName: String) {
    val matcher = hasClickLabel(getTileDescription(tileName))
    composeTestRule.onNode(matcher).assertExists().performClick()
  }

  /**
   * Plays a chess move by clicking on the source tile and then the destination tile.
   *
   * @param fromTile The algebraic notation of the source tile (e.g., "e2")
   * @param toTile The algebraic notation of the destination tile (e.g., "e4")
   */
  fun playMove(fromTile: String, toTile: String) {
    clickOnTile(fromTile)
    clickOnTile(toTile)
  }

  /** Clicks the button to reverse/flip the chess board orientation. */
  fun clickOnReverse() {
    composeTestRule.onNodeWithTag("Reverse board").assertExists().performClick()
  }

  /** Clicks the back button to undo the last move. */
  fun clickOnBack() {
    composeTestRule.onNodeWithTag("Back").assertExists().performClick()
  }

  /** Clicks the next button to redo a previously undone move. */
  fun clickOnNext() {
    composeTestRule.onNodeWithTag("Next").assertExists().performClick()
  }

  /** Clicks the reset button to return the board to its initial state. */
  fun clickOnReset() {
    composeTestRule.onNodeWithTag("Reset board").assertExists().performClick()
  }

  /**
   * Asserts that a specific tile on the chess board is empty (contains no piece).
   *
   * @param tileName The algebraic notation of the tile to check (e.g., "e4")
   */
  fun assertTileIsEmpty(tileName: String) {
    composeTestRule
      .onNode(hasClickLabel(getTileDescription(tileName)))
      .assertExists()
      .assert(hasContentDescription("Piece", substring = true).not())
  }

  /**
   * Asserts that a specific tile on the chess board contains the specified piece.
   *
   * @param tileName The algebraic notation of the tile to check (e.g., "e4")
   * @param piece The chess piece that should be on the tile
   */
  fun assertTileContainsPiece(tileName: String, piece: IPiece) {
    composeTestRule
      .onNode(hasClickLabel(getTileDescription(tileName)))
      .assertExists()
      .assertContentDescriptionContains("Piece $piece")
  }

  /**
   * Asserts that a piece has moved from one tile to another. This checks that the source tile is
   * empty and the destination tile contains the specified piece.
   *
   * @param fromTile The algebraic notation of the source tile (e.g., "e2")
   * @param toTile The algebraic notation of the destination tile (e.g., "e4")
   * @param piece The chess piece that should have moved
   */
  fun assertPieceMoved(fromTile: String, toTile: String, piece: IPiece) {
    assertTileIsEmpty(fromTile)
    assertTileContainsPiece(toTile, piece)
  }

  /**
   * Asserts that a specific move exists in the next move suggestions and returns the node.
   *
   * @param move The algebraic notation of the move (e.g., "e4")
   * @return The SemanticsNodeInteraction representing the move suggestion
   */
  fun assertNextMoveExist(move: String): SemanticsNodeInteraction {
    return composeTestRule.onNodeWithTag(getNextMoveDescription(move)).assertExists()
  }

  // GENERAL

  /**
   * Asserts that a UI element with the specified tag exists and returns it.
   *
   * @param tag The tag used to identify the UI element
   * @return The SemanticsNodeInteraction representing the found element
   * @throws AssertionError if no element with the specified tag exists
   */
  fun assertNodeWithTagExists(tag: String): SemanticsNodeInteraction {
    return composeTestRule.onNodeWithTag(tag).assertExists()
  }

  /**
   * Asserts that no UI element with the specified tag exists.
   *
   * @param tag The tag that should not be present in the UI
   * @throws AssertionError if an element with the specified tag exists
   */
  fun assertNodeWithTagDoesNotExists(tag: String) {
    composeTestRule.onNodeWithTag(tag).assertDoesNotExist()
  }

  /**
   * Asserts that a UI element with the specified text exists and returns it.
   *
   * @param text The text to search for in the UI
   * @return The SemanticsNodeInteraction representing the found element
   * @throws AssertionError if no element with the specified text exists
   */
  fun assertNodeWithTextExists(text: String): SemanticsNodeInteraction {
    return composeTestRule.onNodeWithText(text).assertExists()
  }

  /**
   * Asserts that no UI element with the specified text exists.
   *
   * @param text The text that should not be present in the UI
   * @throws AssertionError if an element with the specified text exists
   */
  fun assertNodeWithTextDoesNotExists(text: String) {
    composeTestRule.onNodeWithText(text).assertDoesNotExist()
  }

  // DATABASE

  /**
   * Retrieves all chess positions stored in the database.
   *
   * @return A list of all stored nodes in the database
   */
  fun getAllPositions(): List<StoredNode> {
    lateinit var allPositions: List<StoredNode>
    runTest { allPositions = DatabaseHolder.getDatabase().getAllPositions() }
    return allPositions
  }

  /**
   * Retrieves a specific chess position from the database.
   *
   * @param positionKey The key identifying the position to retrieve
   * @return The stored node for the position, or null if not found
   */
  fun getPosition(positionKey: PositionKey): StoredNode? {
    var position: StoredNode? = null
    runTest { position = DatabaseHolder.getDatabase().getPosition(positionKey) }
    return position
  }

  /** Clicks the "Save Good" button to mark the current position as a good move. */
  fun clickOnSaveGood() {
    composeTestRule.onNodeWithContentDescription("Save Good").assertExists().performClick()
  }

  /** Clicks the "Save Bad" button to mark the current position as a bad move. */
  fun clickOnSaveBad() {
    composeTestRule.onNodeWithContentDescription("Save Bad").assertExists().performClick()
  }
}
