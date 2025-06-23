package proj.memorchess.axl.factories

import androidx.compose.ui.test.performClick
import kotlin.test.BeforeTest
import kotlin.test.Test
import proj.memorchess.axl.AUiTestFromMainActivity
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.utils.Awaitility

class TestSettingsFactory : AUiTestFromMainActivity() {

  @BeforeTest
  override fun setUp() {
    super.setUp()
    goToSettings()
  }

  @Test
  fun testEraseAllDataButton() {
    assertNodeWithTextDoesNotExists("Confirm?")

    // Verify the confirmation dialog appears and click on "Cancel"
    assertNodeWithTagExists("eraseAllDataButton").performClick()
    assertNodeWithTextExists("Confirm?")
    assertNodeWithTextExists("Cancel").performClick()

    assert(getAllPositions().isNotEmpty()) { "Database should not have been cleared after cancel" }

    // Verify the confirmation dialog appears and click OK
    assertNodeWithTagExists("eraseAllDataButton").performClick()
    assertNodeWithTextExists("OK").performClick()

    // Verify the database is cleared
    Awaitility.awaitUntilTrue(TEST_TIMEOUT) { getAllPositions().isEmpty() }
  }
}
