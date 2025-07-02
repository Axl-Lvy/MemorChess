package proj.memorchess.axl.ui.components.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import proj.memorchess.axl.core.config.AppConfigItem
import proj.memorchess.axl.core.config.MINIMUM_LOADING_TIME_SETTING
import proj.memorchess.axl.core.config.ON_SUCCESS_DATE_FACTOR_SETTING

/**
 * Embedded setting item ready to be drawn.
 *
 * @property configItem The corresponding config item
 * @property buttonParams The button parameters
 */
enum class EmbeddedSettingItem(
  val configItem: AppConfigItem<*, *>?,
  val buttonParams: IButtonParams,
) {
  ON_SUCCESS_DATE_FACTOR(
    ON_SUCCESS_DATE_FACTOR_SETTING,
    SliderParams(
      1f,
      3f,
      9,
      { ON_SUCCESS_DATE_FACTOR_SETTING.setValue(it.toDouble()) },
      { (it as Double).toFloat() },
    ) {
      "On Success Date Factor: ${(it * 100).toInt() / 100.0}"
    },
  ),
  MINIMUM_LOADING_TIME(
    MINIMUM_LOADING_TIME_SETTING,
    SliderParams(
      0f,
      5f,
      49,
      { MINIMUM_LOADING_TIME_SETTING.setValue(it.toDouble().seconds) },
      { (it as Duration).inWholeMilliseconds.toFloat() / 1_000 },
    ) {
      "Minimum loading time: ${(it * 100).roundToInt() / 100.0}s"
    },
  );

  /**
   * Draws this setting item.
   *
   * @param reloadKey The key to trigger the reload of this component
   */
  @Composable
  fun Draw(reloadKey: Any) {
    when (this.buttonParams) {
      is SliderParams -> DrawSlider(reloadKey)
      else -> throw UnsupportedOperationException("Unsupported button type")
    }
  }

  @Composable
  private fun DrawSlider(reloadKey: Any) {
    checkNotNull(configItem) { "A slider needs a non-null config item" }
    val sliderParams = buttonParams as SliderParams
    val value =
      remember(reloadKey) { mutableStateOf(sliderParams.convertToUnit(configItem.getValue())) }

    Column(modifier = Modifier.fillMaxWidth()) {
      Text(text = sliderParams.displayText(value.value))
      Slider(
        value = value.value,
        onValueChange = {
          value.value = it
          sliderParams.setCallBack(it)
        },
        valueRange = sliderParams.min..sliderParams.max,
        steps = sliderParams.steps,
        modifier = Modifier.testTag(configItem.name),
      )
    }
  }
}
