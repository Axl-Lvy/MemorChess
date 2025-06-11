package proj.akichess.axl.database

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.Rule
import proj.memorchess.axl.core.data.DatabaseHolder
import proj.memorchess.axl.test_util.TestDatabase
import proj.memorchess.axl.ui.pages.Settings

class TestSettings {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var database: TestDatabase

  @BeforeTest
  fun setUp() {
    // Initialize with some data
    database = TestDatabase.vienna()
    DatabaseHolder.init(database)

    // Verify we have data in the database
    assert(database.storedNodes.isNotEmpty()) { "Database should have data before test" }

    composeTestRule.setContent { Settings() }
  }

  @Test
  fun testEraseAllDataButton() {

    // Find and click the Erase All Data button
    composeTestRule.onNodeWithTag("eraseAllDataButton").assertExists().performClick()

    // Verify the confirmation dialog appears and click on "Cancel"
    composeTestRule.onNodeWithText("Confirm?").assertExists()
    composeTestRule.onNodeWithText("Cancel").assertExists().performClick()
    assert(database.storedNodes.isNotEmpty()) {
      "Database should not have been cleared after cancel"
    }

    // Find and click the Erase All Data button
    composeTestRule.onNodeWithTag("eraseAllDataButton").assertExists().performClick()

    // Verify the confirmation dialog appears and click OK
    composeTestRule.onNodeWithText("Confirm?").assertExists()
    composeTestRule.onNodeWithText("OK").assertExists().performClick()

    // Verify the database is cleared
    assert(database.storedNodes.isEmpty()) { "Database should be empty after erasing all data" }
  }
}
