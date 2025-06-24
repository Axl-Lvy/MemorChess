package proj.memorchess.axl

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.BeforeTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import proj.memorchess.axl.core.config.IAppConfig
import proj.memorchess.axl.core.data.DatabaseHolder
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.engine.pieces.IPiece
import proj.memorchess.axl.game.getScandinavian
import proj.memorchess.axl.game.getVienna
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.test_util.TestConfig
import proj.memorchess.axl.test_util.TestDatabase
import proj.memorchess.axl.test_util.getNavigationButtonDescription
import proj.memorchess.axl.test_util.getNextMoveDescription
import proj.memorchess.axl.test_util.getTileDescription
import proj.memorchess.axl.ui.pages.navigation.Destination
import proj.memorchess.axl.ui.util.DateUtil
import proj.memorchess.axl.utils.Awaitility
import proj.memorchess.axl.utils.hasClickLabel

/**
 * This class contains a lot of ready-to-use methods for common UI interactions, do not hesitate to
 * use them and to create new one if needed. Tests are way more readable this way.
 */
@OptIn(ExperimentalTestApi::class)
abstract class AUiTestFromMainActivity {
  /**
   * The Compose test rule used to interact with the UI. This is automatically initialized by JUnit
   * before tests are executed.
   */
  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @BeforeTest
  open fun setUp() {
    composeTestRule.mainClock.autoAdvance = true
    IAppConfig.set(TestConfig)
    runTest { resetDatabase() }
  }

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
    waitUntilNodeExists(hasContentDescription(getNavigationButtonDescription(destination.name)))
      .performClick()
  }

  // BOARD

  /**
   * Clicks on a chess board tile with the specified name.
   *
   * @param tileName The algebraic notation of the tile (e.g., "e4", "h8")
   */
  fun clickOnTile(tileName: String) {
    waitUntilTileAppears(tileName).performClick()
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
    this.waitUntilNodeExists(hasTestTag("Reverse board")).assertExists().performClick()
  }

  /** Clicks the back button to undo the last move. */
  fun clickOnBack() {
    this.waitUntilNodeExists(hasTestTag("Back")).assertExists().performClick()
    runTest { composeTestRule.awaitIdle() }
  }

  /** Clicks the next button to redo a previously undone move. */
  fun clickOnNext() {
    this.waitUntilNodeExists(hasTestTag("Next")).assertExists().performClick()
    runTest { composeTestRule.awaitIdle() }
  }

  /** Clicks the reset button to return the board to its initial state. */
  fun clickOnReset() {
    this.waitUntilNodeExists(hasTestTag("Reset board")).assertExists().performClick()
    runTest { composeTestRule.awaitIdle() }
  }

  /**
   * Asserts that a specific tile on the chess board is empty (contains no piece).
   *
   * @param tileName The algebraic notation of the tile to check (e.g., "e4")
   */
  fun assertTileIsEmpty(tileName: String) {
    waitUntilTileAppears(tileName).assert(hasContentDescription("Piece", substring = true).not())
  }

  /**
   * Asserts that a specific tile on the chess board contains the specified piece.
   *
   * @param tileName The algebraic notation of the tile to check (e.g., "e4")
   * @param piece The chess piece that should be on the tile
   */
  fun assertTileContainsPiece(tileName: String, piece: IPiece) {
    waitUntilTileAppears(tileName).assertContentDescriptionContains("Piece $piece")
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
  fun assertNextMoveExist(move: String): SemanticsNodeInteraction {
    return composeTestRule.onNodeWithTag(getNextMoveDescription(move)).assertExists()
  }

  private fun waitUntilTileAppears(tileName: String): SemanticsNodeInteraction {
    val matcher = hasClickLabel(getTileDescription(tileName))
    composeTestRule.waitUntilAtLeastOneExists(matcher)
    return composeTestRule.onNode(matcher).assertExists()
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
    return this.waitUntilNodeExists(hasTestTag(tag)).assertExists()
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

  private fun waitUntilNodeExists(matcher: SemanticsMatcher): SemanticsNodeInteraction {
    composeTestRule.waitUntilAtLeastOneExists(matcher)
    return composeTestRule.onNode(matcher).assertExists()
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
    this.waitUntilNodeExists(hasContentDescription("Save Good", ignoreCase = true))
      .assertExists()
      .performClick()
  }

  /** Clicks the "Save Bad" button to mark the current position as a bad move. */
  fun clickOnSaveBad() {
    this.waitUntilNodeExists(hasContentDescription("Save Bad", ignoreCase = true))
      .assertExists()
      .performClick()
  }

  /**
   * Resets the database to a known initial state with test data.
   *
   * This method:
   * 1. Deletes all existing nodes and moves from the database
   * 2. Waits for the database to be empty
   * 3. Populates the database with test data (Vienna and Scandinavian openings)
   * 4. Waits for the database to be populated
   *
   * The test data includes:
   * - Vienna opening positions (with yesterday/today dates)
   * - Scandinavian opening positions (with older dates)
   *
   * This provides a consistent starting point for tests that require database access.
   */
  suspend fun resetDatabase() {
    Awaitility.awaitUntilTrue(TEST_TIMEOUT, failingMessage = "Database not empty") {
      runTest {
        DatabaseHolder.getDatabase().deleteAllNodes()
        DatabaseHolder.getDatabase().deleteAllMoves()
      }
      lateinit var allPositions: List<StoredNode>
      lateinit var allMoves: List<StoredMove>
      runTest {
        allPositions = DatabaseHolder.getDatabase().getAllPositions()
        allMoves = DatabaseHolder.getDatabase().getAllMoves()
      }
      allPositions.isEmpty() && allMoves.isEmpty()
    }
    val viennaNodes =
      TestDatabase.convertStringMovesToNodes(getVienna()).map {
        StoredNode(
          it.positionKey,
          it.previousMoves,
          it.nextMoves,
          DateUtil.yesterday(),
          DateUtil.today(),
        )
      }
    val scandinavianNodes =
      TestDatabase.convertStringMovesToNodes(getScandinavian()).map {
        StoredNode(
          it.positionKey,
          it.previousMoves,
          it.nextMoves,
          DateUtil.dateInDays(-2),
          DateUtil.tomorrow(),
        )
      }
    val storedNodes = (viennaNodes + scandinavianNodes)
    for (node in storedNodes) {
      DatabaseHolder.getDatabase().insertPosition(node)
    }
    Awaitility.awaitUntilTrue(TEST_TIMEOUT, failingMessage = "Database not populated") {
      lateinit var allPositions: List<StoredNode>
      runBlocking { allPositions = DatabaseHolder.getDatabase().getAllPositions() }
      allPositions.size == storedNodes.map { it.positionKey }.distinct().size
    }
  }
}
