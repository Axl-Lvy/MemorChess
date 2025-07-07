package proj.memorchess.axl.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import proj.memorchess.axl.core.config.APP_THEME_SETTING
import proj.memorchess.axl.core.config.EnumBasedAppConfigItem
import proj.memorchess.axl.core.config.IConfigItem
import proj.memorchess.axl.core.config.ON_SUCCESS_DATE_FACTOR_SETTING
import proj.memorchess.axl.core.config.TRAINING_MOVE_DELAY_SETTING

/**
 * Embedded setting item ready to be drawn.
 *
 * @property configItem The corresponding config item
 * @property buttonParams The button parameters
 */
enum class EmbeddedSettingItem(val configItem: IConfigItem<*>, val buttonParams: IButtonParams) {
  ON_SUCCESS_DATE_FACTOR(
    ON_SUCCESS_DATE_FACTOR_SETTING,
    SliderParams(
      1f,
      5f,
      7,
      { ON_SUCCESS_DATE_FACTOR_SETTING.setValue(it.toDouble()) },
      { (it as Double).toFloat() },
    ) {
      "On Success Date Factor: ${(it * 100).toInt() / 100.0}"
    },
  ),
  TRAINING_MOVE_DELAY(
    TRAINING_MOVE_DELAY_SETTING,
    SliderParams(
      0f,
      5f,
      49,
      { TRAINING_MOVE_DELAY_SETTING.setValue(it.toDouble().seconds) },
      { (it as Duration).inWholeMilliseconds.toFloat() / 1_000 },
    ) {
      "Delay before next move: ${(it * 100).roundToInt() / 100.0}s"
    },
  ),
  APP_THEME(APP_THEME_SETTING);

  constructor(
    enumItem: EnumBasedAppConfigItem<*>
  ) : this(enumItem, EnumBasedSelectorParameters(enumItem))

  /**
   * Draws this setting item.
   *
   * @param reloadKey The key to trigger the reload of this component
   */
  @Composable
  fun Draw(reloadKey: Any) {
    when (this.buttonParams) {
      is SliderParams -> DrawSlider(reloadKey)
      is EnumBasedSelectorParameters<*> -> DrawItemSelector(reloadKey, this.buttonParams)
      else -> throw UnsupportedOperationException("Unsupported button type")
    }
  }

  @Composable
  private fun DrawSlider(reloadKey: Any) {
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

  @Composable
  private fun <T : Enum<T>> DrawItemSelector(
    reloadKey: Any,
    buttonParams: EnumBasedSelectorParameters<T>,
  ) {
    val options = buttonParams.config.getEntries()
    val selected = remember(reloadKey) { mutableStateOf(buttonParams.config.getValue()) }

    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))) {
      options.forEach { option ->
        val isSelected = option == selected.value
        Box(
          modifier =
            Modifier.weight(1f)
              .fillMaxWidth()
              .then(
                if (isSelected)
                  Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
                else
                  Modifier.background(
                    androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                  )
              )
              .testTag("${buttonParams.config.name}_$option")
              .clickable(enabled = !isSelected) {
                selected.value = option
                buttonParams.config.setValue(option)
              },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = option.name,
            color =
              if (isSelected) androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
              else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
          )
        }
      }
    }
  }
}
