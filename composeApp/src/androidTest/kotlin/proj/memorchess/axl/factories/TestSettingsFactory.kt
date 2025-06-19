package proj.memorchess.axl.factories

import androidx.compose.ui.test.performClick
import kotlin.time.Duration.Companion.seconds
import proj.memorchess.axl.AUiTestFactory
import proj.memorchess.axl.utils.Awaitility

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

    // Verify the database is cleared
    Awaitility.awaitUntilTrue(5.seconds) { getAllPosition().isEmpty() }
  }
}
