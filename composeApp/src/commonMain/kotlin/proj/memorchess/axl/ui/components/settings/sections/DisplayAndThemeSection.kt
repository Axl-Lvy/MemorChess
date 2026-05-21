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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import proj.memorchess.axl.core.config.APP_THEME_SETTING
import proj.memorchess.axl.core.config.MOVE_ANIMATION_DURATION_SETTING
import proj.memorchess.axl.ui.components.controls.KineticSegmentedControl
import proj.memorchess.axl.ui.components.controls.KineticSlider
import proj.memorchess.axl.ui.theme.AppThemeSetting

/**
 * Display & Theme settings section content. Renders the app theme picker (LIGHT/DARK/SYSTEM) and
 * the move animation duration slider.
 *
 * @param reloadKey value used to force re-reading the persisted settings on external reloads.
 * @param onReload called after the user changes the app theme so the parent can refresh dependent
 *   composables.
 */
@Composable
fun DisplayAndThemeSection(reloadKey: Any, onReload: () -> Unit = {}) {
  var theme by remember(reloadKey) { mutableStateOf<AppThemeSetting>(APP_THEME_SETTING.getValue()) }
  var animation by
    remember(reloadKey) {
      mutableStateOf(
        MOVE_ANIMATION_DURATION_SETTING.getValue().inWholeMilliseconds.toFloat() / 1_000f
      )
    }

  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    KineticSegmentedControl(
      options = AppThemeSetting.entries.toList(),
      selected = theme,
      onSelect = {
        theme = it
        APP_THEME_SETTING.setValue(it)
        onReload()
      },
      modifier = Modifier.fillMaxWidth().testTag(APP_THEME_SETTING.name),
      label = { it.displayName },
    )

    KineticSlider(
      value = animation,
      onValueChange = {
        animation = it
        MOVE_ANIMATION_DURATION_SETTING.setValue(it.toDouble().seconds)
      },
      range = 0f..2f,
      label = "Move animation",
      valueFormatter = { (it * 1000).roundToInt().toString() },
      unit = "ms",
      minLabel = "0",
      maxLabel = "2000ms",
      sliderTestTag = MOVE_ANIMATION_DURATION_SETTING.name,
    )
  }
}
