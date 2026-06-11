package proj.memorchess.axl.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.inject
import proj.memorchess.axl.core.config.TRAINING_MOVE_DELAY_SETTING
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.scheduling.CardStateFactory
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.pages.SETTINGS_SECTION_LIST_TAG
import proj.memorchess.axl.ui.pages.Settings

@OptIn(ExperimentalTestApi::class)
class TestSettings : TestWithKoin() {

  private val database: DatabaseQueryManager by inject()

  private fun runTestFromSetup(block: suspend ComposeUiTest.() -> Unit) = runComposeUiTest {
    koinSetUp()
    try {
      setContent { InitializeApp { Settings() } }
      block()
    } finally {
      koinTearDown()
    }
  }

  @Test
  fun testTrainingMoveDelaySlider() = runTestFromSetup {
    // Verify the slider exists
    assertNodeWithTagExists(TRAINING_MOVE_DELAY_SETTING.name)

    slideToRight(TRAINING_MOVE_DELAY_SETTING.name)
    assertTrue { TRAINING_MOVE_DELAY_SETTING.getValue() > 2.seconds }
    slideToLeft(TRAINING_MOVE_DELAY_SETTING.name)
    assertTrue { TRAINING_MOVE_DELAY_SETTING.getValue() < 2.seconds }
  }

  @Test
  fun testResetButton() = runTestFromSetup {

    // Set non default values
    TRAINING_MOVE_DELAY_SETTING.setValue(3.0.seconds)

    // The Danger Zone is the last LazyColumn section, so it is not composed until scrolled into
    // view. Scroll the list to the reset button before interacting with it.
    onNodeWithTag(SETTINGS_SECTION_LIST_TAG).performScrollToNode(hasTestTag("resetConfigButton"))
    assertNodeWithTagExists("resetConfigButton").performClick()
    assertNodeWithTagExists("confirmDialog")
    assertNodeWithTagExists("confirmDialogOkButton").performClick()

    // Verify the values were reset to defaults
    waitUntil(timeoutMillis = TEST_TIMEOUT.inWholeMilliseconds) {
      TRAINING_MOVE_DELAY_SETTING.defaultValue == TRAINING_MOVE_DELAY_SETTING.getValue()
    }
  }

  @Test
  fun testEraseAllDataButton() = runTestFromSetup {
    database.insertNodes(
      DataNode(PositionKey.START_POSITION, PreviousAndNextMoves(), CardStateFactory.new())
    )
    assertNodeWithTagDoesNotExists("confirmDialog")

    // The Danger Zone is the last LazyColumn section, so it is not composed until scrolled into
    // view. Scroll the list to the erase button before interacting with it.
    onNodeWithTag(SETTINGS_SECTION_LIST_TAG).performScrollToNode(hasTestTag("eraseAllDataButton"))

    // Verify the confirmation dialog appears and click on "Cancel"
    assertNodeWithTagExists("eraseAllDataButton").performClick()
    assertNodeWithTagExists("confirmDialog")
    assertNodeWithTagExists("confirmDialogCancelButton").performClick()

    val positionsAfterCancel = database.getAllNodes(false)
    assertTrue("Database should not have been cleared after cancel") {
      positionsAfterCancel.isNotEmpty()
    }

    // Verify the confirmation dialog appears and click OK
    assertNodeWithTagExists("eraseAllDataButton").performClick()
    assertNodeWithTagExists("confirmDialogOkButton").performClick()

    // Verify the database is cleared
    waitUntilSuspending { database.getAllNodes(false).isEmpty() }
  }
}
