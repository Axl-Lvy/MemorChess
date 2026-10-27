package proj.memorchess.axl.ui.components.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.settings_auto_advance_delay
import memorchess.composeapp.generated.resources.settings_auto_advance_max
import memorchess.composeapp.generated.resources.settings_auto_advance_min
import memorchess.composeapp.generated.resources.settings_fuzz
import memorchess.composeapp.generated.resources.settings_max_new_moves_per_day
import memorchess.composeapp.generated.resources.settings_max_total_moves_per_day
import memorchess.composeapp.generated.resources.settings_relearn_failed
import memorchess.composeapp.generated.resources.settings_unit_seconds
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.config.FUZZ_ENABLED_SETTING
import proj.memorchess.axl.core.config.MAX_NEW_MOVES_PER_DAY_SETTING
import proj.memorchess.axl.core.config.MAX_TOTAL_MOVES_PER_DAY_SETTING
import proj.memorchess.axl.core.config.SHORT_TERM_ENABLED_SETTING
import proj.memorchess.axl.core.config.TRAINING_MOVE_DELAY_SETTING
import proj.memorchess.axl.ui.components.controls.KineticNumberField
import proj.memorchess.axl.ui.components.controls.KineticSlider
import proj.memorchess.axl.ui.components.controls.KineticSliderLabels
import proj.memorchess.axl.ui.components.controls.KineticToggleRow

/**
 * Training behavior settings: auto-advance delay, learning steps, fuzz and daily limits. There is
 * deliberately no scheduling algorithm picker, since FSRS 6 is the only algorithm.
 *
 * @param reloadKey Changing it re-reads the persisted settings.
 */
@Composable
fun TrainingBehaviorSection(reloadKey: Any) {
  var value by
    remember(reloadKey) {
      mutableStateOf(TRAINING_MOVE_DELAY_SETTING.getValue().inWholeMilliseconds.toFloat() / 1_000f)
    }
  var fuzzEnabled by remember(reloadKey) { mutableStateOf(FUZZ_ENABLED_SETTING.getValue()) }
  var shortTermEnabled by
    remember(reloadKey) { mutableStateOf(SHORT_TERM_ENABLED_SETTING.getValue()) }
  var maxNewMovesPerDay by
    remember(reloadKey) { mutableStateOf(MAX_NEW_MOVES_PER_DAY_SETTING.getValue()) }
  var maxTotalMovesPerDay by
    remember(reloadKey) { mutableStateOf(MAX_TOTAL_MOVES_PER_DAY_SETTING.getValue()) }
  val delayLabel = stringResource(Res.string.settings_auto_advance_delay)
  val sUnit = stringResource(Res.string.settings_unit_seconds)
  val minLabel = stringResource(Res.string.settings_auto_advance_min)
  val maxLabel = stringResource(Res.string.settings_auto_advance_max)

  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    KineticSlider(
      value = value,
      onValueChange = {
        value = it
        TRAINING_MOVE_DELAY_SETTING.setValue(it.toDouble().seconds)
      },
      modifier = Modifier.fillMaxWidth(),
      range = 0f..5f,
      labels =
        KineticSliderLabels(
          label = delayLabel,
          valueFormatter = { ((it * 100).roundToInt() / 100.0).toString() },
          unit = sUnit,
          minLabel = minLabel,
          maxLabel = maxLabel,
        ),
      sliderTestTag = TRAINING_MOVE_DELAY_SETTING.name,
    )
    KineticToggleRow(
      label = stringResource(Res.string.settings_relearn_failed),
      checked = shortTermEnabled,
      onCheckedChange = {
        shortTermEnabled = it
        SHORT_TERM_ENABLED_SETTING.setValue(it)
      },
      testTag = SHORT_TERM_ENABLED_SETTING.name,
    )
    KineticToggleRow(
      label = stringResource(Res.string.settings_fuzz),
      checked = fuzzEnabled,
      onCheckedChange = {
        fuzzEnabled = it
        FUZZ_ENABLED_SETTING.setValue(it)
      },
      testTag = FUZZ_ENABLED_SETTING.name,
    )
    KineticNumberField(
      value = maxNewMovesPerDay,
      onValueCommit = {
        maxNewMovesPerDay = it
        MAX_NEW_MOVES_PER_DAY_SETTING.setValue(it)
      },
      label = stringResource(Res.string.settings_max_new_moves_per_day),
      testTag = MAX_NEW_MOVES_PER_DAY_SETTING.name,
    )
    KineticNumberField(
      value = maxTotalMovesPerDay,
      onValueCommit = {
        maxTotalMovesPerDay = it
        MAX_TOTAL_MOVES_PER_DAY_SETTING.setValue(it)
      },
      label = stringResource(Res.string.settings_max_total_moves_per_day),
      testTag = MAX_TOTAL_MOVES_PER_DAY_SETTING.name,
    )
  }
}
