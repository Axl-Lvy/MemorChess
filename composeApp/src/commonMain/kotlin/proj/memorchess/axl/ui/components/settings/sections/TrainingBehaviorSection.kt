package proj.memorchess.axl.ui.components.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import proj.memorchess.axl.core.config.FUZZ_ENABLED_SETTING
import proj.memorchess.axl.core.config.TRAINING_MOVE_DELAY_SETTING
import proj.memorchess.axl.ui.components.controls.KineticSlider
import proj.memorchess.axl.ui.components.controls.KineticSliderLabels
import proj.memorchess.axl.ui.components.controls.KineticToggle
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * Training Behavior settings section content.
 *
 * Exposes the auto-advance delay slider backed by [TRAINING_MOVE_DELAY_SETTING] and the scheduling
 * fuzz toggle backed by [FUZZ_ENABLED_SETTING].
 *
 * **Deliberate omissions:**
 * - No scheduling-algorithm picker. The HTML proposal shows an SM-2 / FSRS 6 segmented control, but
 *   FSRS 6 is the only algorithm and the user has explicitly excluded surfacing this choice. Do not
 *   re-add it without removing this comment.
 * - No "Days in advance" slider. The HTML proposal shows one; that value is currently in-page state
 *   only and persisting it would require adding a new `ConfigItem` under `core/config/`, which is
 *   out of scope for the v1 settings page rebuild. Follow-up: thread a persisted setting in once
 *   the config layer is unlocked.
 *
 * @param reloadKey value used to force re-reading the persisted setting on external reloads.
 */
@Composable
fun TrainingBehaviorSection(reloadKey: Any) {
  var value by
    remember(reloadKey) {
      mutableStateOf(TRAINING_MOVE_DELAY_SETTING.getValue().inWholeMilliseconds.toFloat() / 1_000f)
    }
  var fuzzEnabled by remember(reloadKey) { mutableStateOf(FUZZ_ENABLED_SETTING.getValue()) }

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
          label = "Auto-advance delay",
          valueFormatter = { ((it * 100).roundToInt() / 100.0).toString() },
          unit = "s",
          minLabel = "0s",
          maxLabel = "5s",
        ),
      sliderTestTag = TRAINING_MOVE_DELAY_SETTING.name,
    )
    ToggleRow(
      label = "Spread review intervals (fuzz)",
      checked = fuzzEnabled,
      onCheckedChange = {
        fuzzEnabled = it
        FUZZ_ENABLED_SETTING.setValue(it)
      },
      testTag = FUZZ_ENABLED_SETTING.name,
    )
  }
}

/** A label-on-left, toggle-on-right row used inside the Training Behavior section. */
@Composable
private fun ToggleRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(text = label, style = typography.body.copy(color = palette.ink2))
    KineticToggle(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(testTag),
    )
  }
}
