package proj.memorchess.axl.factories

import androidx.compose.ui.test.performClick
import proj.memorchess.axl.AUiTestFactory
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.util.AwaitUtil
import proj.memorchess.axl.util.UiTest

class TestSettingsFactory : AUiTestFactory() {

  override fun beforeEach() {
    goToSettings()
  }

  override fun needsDatabaseReset(): Boolean = true

  @UiTest
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
    AwaitUtil.awaitUntilTrue(TEST_TIMEOUT) { getAllPositions().isEmpty() }
  }
}
