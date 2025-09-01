package proj.memorchess.axl.ui

import androidx.compose.ui.test.*
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.koin.core.component.inject
import proj.memorchess.axl.core.config.ON_SUCCESS_DATE_FACTOR_SETTING
import proj.memorchess.axl.core.config.TRAINING_MOVE_DELAY_SETTING
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.date.PreviousAndNextDate
import proj.memorchess.axl.core.graph.nodes.PreviousAndNextMoves
import proj.memorchess.axl.test_util.Awaitility
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.pages.Settings

@OptIn(ExperimentalTestApi::class)
class TestSettings : TestWithKoin {

  private val database: DatabaseQueryManager by inject()

  fun runTestFromSetup(block: ComposeUiTest.() -> Unit) {
    runComposeUiTest {
      setContent { InitializeApp { Settings() } }
      block()
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
  fun testOnSuccessDateFactorSlider() = runTestFromSetup {
    // Verify the slider exists
    assertNodeWithTagExists(ON_SUCCESS_DATE_FACTOR_SETTING.name)

    slideToRight(ON_SUCCESS_DATE_FACTOR_SETTING.name)
    assertTrue(ON_SUCCESS_DATE_FACTOR_SETTING.getValue() > 2.0)
    slideToLeft(ON_SUCCESS_DATE_FACTOR_SETTING.name)
    assertTrue(ON_SUCCESS_DATE_FACTOR_SETTING.getValue() < 2.0)
  }

  @Test
  fun testResetButton() = runTestFromSetup {

    // Set non-default values
    TRAINING_MOVE_DELAY_SETTING.setValue(3.0.seconds)
    ON_SUCCESS_DATE_FACTOR_SETTING.setValue(2.5)

    // Click the reset button
    assertNodeWithTagExists("resetConfigButton").performScrollTo().performClick()
    assertNodeWithTagExists("confirmDialog")
    assertNodeWithTextExists("OK").performClick()

    // Verify the values were reset to defaults
    Awaitility.awaitUntilTrue {
      TRAINING_MOVE_DELAY_SETTING.defaultValue == TRAINING_MOVE_DELAY_SETTING.getValue() &&
        ON_SUCCESS_DATE_FACTOR_SETTING.defaultValue == ON_SUCCESS_DATE_FACTOR_SETTING.getValue()
    }
  }

  @Test
  fun testEraseAllDataButton() = runTestFromSetup {
    runTest {
      database.insertNodes(
        DataNode(
          PositionIdentifier.START_POSITION,
          PreviousAndNextMoves(),
          PreviousAndNextDate(DateUtil.today(), DateUtil.today()),
        )
      )
    }
    assertNodeWithTagDoesNotExists("confirmDialog")

    // Verify the confirmation dialog appears and click on "Cancel"
    assertNodeWithTagExists("eraseAllDataButton").performScrollTo().performClick()
    assertNodeWithTagExists("confirmDialog")
    assertNodeWithTextExists("Cancel").performClick()

    assertTrue("Database should not have been cleared after cancel") {
      getAllPositions().isNotEmpty()
    }

    // Verify the confirmation dialog appears and click OK
    assertNodeWithTagExists("eraseAllDataButton").performClick()
    assertNodeWithTextExists("OK").performClick()

    // Verify the database is cleared
    Awaitility.awaitUntilTrue(TEST_TIMEOUT) { getAllPositions().isEmpty() }
  }

  private fun getAllPositions(): List<DataNode> {
    var result: List<DataNode>? = null
    runTest { result = database.getAllNodes(false) }
    checkNotNull(result)
    return result
  }
}
