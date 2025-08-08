package proj.memorchess.axl.settings

import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import proj.memorchess.axl.core.config.ON_SUCCESS_DATE_FACTOR_SETTING
import proj.memorchess.axl.core.config.TRAINING_MOVE_DELAY_SETTING
import proj.memorchess.axl.test_util.TEST_TIMEOUT
import proj.memorchess.axl.utils.Awaitility
import proj.memorchess.axl.utils.UiTestFromMainActivity

class TestSettings : UiTestFromMainActivity() {

  @BeforeTest
  override fun setUp() {
    super.setUp()
    goToSettings()
  }

  @Test
  fun testTrainingMoveDelaySlider() {
    // Verify the slider exists
    assertNodeWithTagExists(TRAINING_MOVE_DELAY_SETTING.name)

    slideToRight(TRAINING_MOVE_DELAY_SETTING.name)
    assert(TRAINING_MOVE_DELAY_SETTING.getValue() > 2.seconds)
    slideToLeft(TRAINING_MOVE_DELAY_SETTING.name)
    assert(TRAINING_MOVE_DELAY_SETTING.getValue() < 2.seconds)
  }

  @Test
  fun testOnSuccessDateFactorSlider() {
    // Verify the slider exists
    assertNodeWithTagExists(ON_SUCCESS_DATE_FACTOR_SETTING.name)

    slideToRight(ON_SUCCESS_DATE_FACTOR_SETTING.name)
    assert(ON_SUCCESS_DATE_FACTOR_SETTING.getValue() > 2.0)
    slideToLeft(ON_SUCCESS_DATE_FACTOR_SETTING.name)
    assert(ON_SUCCESS_DATE_FACTOR_SETTING.getValue() < 2.0)
  }

  @Test
  fun testResetButton() {

    // Set non-default values
    TRAINING_MOVE_DELAY_SETTING.setValue(3.0.seconds)
    ON_SUCCESS_DATE_FACTOR_SETTING.setValue(2.5)

    // Click the reset button
    assertNodeWithTagExists("resetConfigButton").performScrollTo().performClick()
    assertNodeWithTagExists("confirmDialog")
    assertNodeWithTextExists("OK").performClick()

    // Verify the values were reset to defaults
    assertEquals(TRAINING_MOVE_DELAY_SETTING.defaultValue, TRAINING_MOVE_DELAY_SETTING.getValue())
    assertEquals(
      ON_SUCCESS_DATE_FACTOR_SETTING.defaultValue,
      ON_SUCCESS_DATE_FACTOR_SETTING.getValue(),
    )
  }

  @Test
  fun testEraseAllDataButton() {
    assertNodeWithTagDoesNotExists("confirmDialog")

    // Verify the confirmation dialog appears and click on "Cancel"
    assertNodeWithTagExists("eraseAllDataButton").performScrollTo().performClick()
    assertNodeWithTagExists("confirmDialog")
    assertNodeWithTextExists("Cancel").performClick()

    assert(getAllPositions().isNotEmpty()) { "Database should not have been cleared after cancel" }

    // Verify the confirmation dialog appears and click OK
    assertNodeWithTagExists("eraseAllDataButton").performClick()
    assertNodeWithTextExists("OK").performClick()

    // Verify the database is cleared
    Awaitility.awaitUntilTrue(TEST_TIMEOUT) { getAllPositions().isEmpty() }
  }
}
