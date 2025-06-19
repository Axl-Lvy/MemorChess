package proj.memorchess.axl.factories

import androidx.compose.ui.test.performClick
import proj.memorchess.axl.AUiTestFactory

class TestSettingsFactory : AUiTestFactory() {
  override fun createTests(): List<() -> Unit> {
    return listOf(::testEraseAllDataButton)
  }

  override fun beforeEach() {
    clickOnSettings()
  }

  fun testEraseAllDataButton() {
    assertNodeWithTextDoesNotExists("Confirm?")

    // Verify the confirmation dialog appears and click on "Cancel"
    assertNodeWithTagExists("eraseAllDataButton").performClick()
    assertNodeWithTextExists("Confirm?")
    assertNodeWithTextExists("Cancel").performClick()

    assert(getAllPosition().isNotEmpty()) { "Database should not have been cleared after cancel" }

    // Verify the confirmation dialog appears and click OK
    assertNodeWithTagExists("eraseAllDataButton").performClick()
    assertNodeWithTextExists("OK").performClick()

    // Verify the database is cleared. We don't need to wait here as the deletion should have been
    // sent before the query.
    assert(getAllPosition().isEmpty()) { "Database should be empty after erasing all data" }
  }
}
