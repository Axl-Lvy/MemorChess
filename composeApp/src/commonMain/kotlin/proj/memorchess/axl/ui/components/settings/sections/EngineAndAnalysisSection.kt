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
import proj.memorchess.axl.core.config.BEST_MOVE_ARROW_ENABLED_SETTING
import proj.memorchess.axl.core.config.ENGINE_MAX_DEPTH_SETTING
import proj.memorchess.axl.core.config.EVAL_BAR_ENABLED_SETTING
import proj.memorchess.axl.ui.components.controls.KineticSlider
import proj.memorchess.axl.ui.components.controls.KineticSliderLabels
import proj.memorchess.axl.ui.components.controls.KineticToggle
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * Engine & Analysis settings section content. Renders two toggle rows (evaluation bar, best-move
 * arrow) and a slider for engine max depth.
 *
 * The depth slider snaps to integer values; selecting the maximum (26) maps to "infinite" depth,
 * persisted as `0` and shown as "∞" to mirror the contract documented on
 * [ENGINE_MAX_DEPTH_SETTING].
 *
 * @param reloadKey value used to force re-reading the persisted settings on external reloads.
 */
@Composable
fun EngineAndAnalysisSection(reloadKey: Any) {
  var evalEnabled by remember(reloadKey) { mutableStateOf(EVAL_BAR_ENABLED_SETTING.getValue()) }
  var arrowEnabled by
    remember(reloadKey) { mutableStateOf(BEST_MOVE_ARROW_ENABLED_SETTING.getValue()) }
  var depth by
    remember(reloadKey) {
      mutableStateOf(
        run {
          val stored = ENGINE_MAX_DEPTH_SETTING.getValue()
          if (stored == 0) 26f else stored.toFloat()
        }
      )
    }

  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    ToggleRow(
      label = "Evaluation bar",
      checked = evalEnabled,
      onCheckedChange = {
        evalEnabled = it
        EVAL_BAR_ENABLED_SETTING.setValue(it)
      },
      testTag = EVAL_BAR_ENABLED_SETTING.name,
    )
    ToggleRow(
      label = "Best-move arrow",
      checked = arrowEnabled,
      onCheckedChange = {
        arrowEnabled = it
        BEST_MOVE_ARROW_ENABLED_SETTING.setValue(it)
      },
      testTag = BEST_MOVE_ARROW_ENABLED_SETTING.name,
    )
    KineticSlider(
      value = depth,
      onValueChange = {
        depth = it
        val intVal = it.roundToInt()
        ENGINE_MAX_DEPTH_SETTING.setValue(if (intVal >= 26) 0 else intVal)
      },
      modifier = Modifier.fillMaxWidth(),
      range = 5f..26f,
      labels =
        KineticSliderLabels(
          label = "Engine depth",
          valueFormatter = {
            val intVal = it.roundToInt()
            if (intVal >= 26) "∞" else intVal.toString()
          },
          minLabel = "5",
          maxLabel = "∞",
        ),
      sliderTestTag = ENGINE_MAX_DEPTH_SETTING.name,
    )
  }
}

/** A label-on-left, toggle-on-right row used inside the Engine & Analysis section. */
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
