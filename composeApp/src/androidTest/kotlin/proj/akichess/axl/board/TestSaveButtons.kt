package proj.akichess.axl.board

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.Rule
import proj.akichess.axl.utils.hasClickLabel
import proj.memorchess.axl.core.data.DatabaseHolder
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.test_util.TestDatabase
import proj.memorchess.axl.test_util.getTileDescription
import proj.memorchess.axl.test_util.setupConfigForTest
import proj.memorchess.axl.ui.components.control.board_control.ControllableBoardPage

class TestSaveButtons {

  @get:Rule val composeTestRule = createComposeRule()

  private var database: TestDatabase = TestDatabase.empty()

  @OptIn(ExperimentalTestApi::class)
  @BeforeTest
  fun setUp() {
    // Use test configuration for faster tests
    setupConfigForTest()
    database = TestDatabase.empty()
    DatabaseHolder.init(database)
    composeTestRule.setContent { ControllableBoardPage() }
    composeTestRule.waitUntilAtLeastOneExists(hasClickLabel(getTileDescription("e2")), 5_000L)
    composeTestRule.onNode(hasClickLabel(getTileDescription("e2"))).assertExists().performClick()
    composeTestRule.onNode(hasClickLabel(getTileDescription("e4"))).assertExists().performClick()
  }

  @Test
  fun testSaveGoodButton() {
    composeTestRule.onNodeWithContentDescription("Save Good").assertExists().performClick()

    // Verify the move was saved as good by checking the database
    val storedNode = database.storedNodes[rootPositionKey.fenRepresentation]
    assert(storedNode != null) { "Node for root position should not be null" }
    val nextMoves = storedNode?.nextMoves
    assert(!nextMoves.isNullOrEmpty()) { "A move should have been saved in the database" }
    val move = nextMoves?.find { it.move == "e4" }
    assert(move != null) { "Move 'e4' should have been saved in the database" }
    assert(move?.isGood == true)
  }

  @Test
  fun testSaveBadButton() {
    composeTestRule.onNodeWithContentDescription("Save Bad").assertExists().performClick()

    // Verify the move was saved as good by checking the database
    assert(
      database.storedNodes[rootPositionKey.fenRepresentation]
        ?.nextMoves
        ?.find { it.move == "e4" }
        ?.isGood == false
    )
  }
}

private val rootPositionKey = Game().position.toImmutablePosition()
