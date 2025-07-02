package proj.memorchess.axl.settings

import androidx.compose.ui.test.performClick
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import proj.memorchess.axl.core.config.MINIMUM_LOADING_TIME_SETTING
import proj.memorchess.axl.core.config.ON_SUCCESS_DATE_FACTOR_SETTING
import proj.memorchess.axl.utils.AUiTestFromMainActivity

class TestSettings : AUiTestFromMainActivity() {

  @BeforeTest
  override fun setUp() {
    super.setUp()
    goToSettings()
  }

  @Test
  fun testMinimumLoadingTimeSlider() {
    // Verify the slider exists
    assertNodeWithTagExists(MINIMUM_LOADING_TIME_SETTING.name)

    slideToRight(MINIMUM_LOADING_TIME_SETTING.name)
    assert(MINIMUM_LOADING_TIME_SETTING.getValue() > 3.seconds)
    slideToLeft(MINIMUM_LOADING_TIME_SETTING.name)
    assert(MINIMUM_LOADING_TIME_SETTING.getValue() < 1.seconds)
  }

  @Test
  fun testOnSuccessDateFactorSlider() {
    // Verify the slider exists
    assertNodeWithTagExists(ON_SUCCESS_DATE_FACTOR_SETTING.name)

    slideToRight(ON_SUCCESS_DATE_FACTOR_SETTING.name)
    assert(ON_SUCCESS_DATE_FACTOR_SETTING.getValue() > 2.5)
    slideToLeft(ON_SUCCESS_DATE_FACTOR_SETTING.name)
    assert(ON_SUCCESS_DATE_FACTOR_SETTING.getValue() < 1.5)
  }

  @Test
  fun testResetButton() {

    // Set non-default values
    MINIMUM_LOADING_TIME_SETTING.setValue(3.0.seconds)
    ON_SUCCESS_DATE_FACTOR_SETTING.setValue(2.5)

    // Click the reset button
    assertNodeWithTagExists("resetConfigButton").performClick()

    // Verify the values were reset to defaults
    assertEquals(0.5.seconds, MINIMUM_LOADING_TIME_SETTING.getValue())
    assertEquals(1.5, ON_SUCCESS_DATE_FACTOR_SETTING.getValue())
  }
}
