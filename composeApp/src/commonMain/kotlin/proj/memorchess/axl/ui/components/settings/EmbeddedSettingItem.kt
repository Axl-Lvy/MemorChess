package proj.memorchess.axl.ui.components.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import proj.memorchess.axl.core.config.APP_THEME_SETTING
import proj.memorchess.axl.core.config.CHESS_BOARD_COLOR_SETTING
import proj.memorchess.axl.core.config.ConfigItem
import proj.memorchess.axl.core.config.EnumBasedAppConfigItem
import proj.memorchess.axl.core.config.ON_SUCCESS_DATE_FACTOR_SETTING
import proj.memorchess.axl.core.config.TRAINING_MOVE_DELAY_SETTING
import proj.memorchess.axl.core.util.CanDisplayName
import proj.memorchess.axl.ui.components.buttons.WideScrollBarChild
import proj.memorchess.axl.ui.components.buttons.WideScrollableRow

/**
 * Embedded setting item ready to be drawn.
 *
 * @property configItem The corresponding config item
 * @property buttonParams The button parameters
 */
enum class EmbeddedSettingItem(
  private val configItem: ConfigItem<*>,
  private val buttonParams: ButtonParameters,
) {
  ON_SUCCESS_DATE_FACTOR(
    ON_SUCCESS_DATE_FACTOR_SETTING,
    SliderParameters(
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
    SliderParameters(
      0f,
      5f,
      49,
      { TRAINING_MOVE_DELAY_SETTING.setValue(it.toDouble().seconds) },
      { (it as Duration).inWholeMilliseconds.toFloat() / 1_000 },
    ) {
      "Delay before next move: ${(it * 100).roundToInt() / 100.0}s"
    },
  ),
  APP_THEME(APP_THEME_SETTING),
  CHESS_BOARD_COLOR(CHESS_BOARD_COLOR_SETTING);

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
      is SliderParameters -> DrawSlider(reloadKey)
      is EnumBasedSelectorParameters<*> -> DrawItemSelector(this.buttonParams)
      else -> throw UnsupportedOperationException("Unsupported button type")
    }
  }

  @Composable
  private fun DrawSlider(reloadKey: Any) {
    val sliderParams = buttonParams as SliderParameters
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
  private fun <T> DrawItemSelector(buttonParams: EnumBasedSelectorParameters<T>) where
  T : Enum<T>,
  T : CanDisplayName {
    val children =
      buttonParams.config.getEntries().map {
        WideScrollBarChild(
          "${buttonParams.config.name}_$it",
          { buttonParams.config.setValue(it) },
          { isSelected ->
            Text(
              text = it.displayName,
              color =
                if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 12.dp),
            )
          },
        )
      }

    WideScrollableRow(
      modifier = Modifier.fillMaxWidth(),
      children = children,
      selectedInitial = buttonParams.config.getValue().ordinal,
      minWidth = 124.dp,
    )
  }
}
