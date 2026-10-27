package proj.memorchess.axl.ui.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * Text and formatting configuration for a [KineticSlider].
 *
 * @property minLabel Left edge label; `null` shows the formatted range start.
 * @property maxLabel Right edge label; `null` shows the formatted range end.
 */
data class KineticSliderLabels(
  val label: String,
  val valueFormatter: (Float) -> String = { it.toString() },
  val unit: String = "",
  val minLabel: String? = null,
  val maxLabel: String? = null,
)

/**
 * Kinetic slider with a label, the formatted value and range edge labels. It skins the native M3
 * [Slider], since overlaying a transparent one on a painted track breaks touch gesture pickup.
 *
 * @param value Current value, clamped into [range].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KineticSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  labels: KineticSliderLabels,
  modifier: Modifier = Modifier,
  range: ClosedFloatingPointRange<Float> = 0f..1f,
  enabled: Boolean = true,
  sliderTestTag: String? = null,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val clamped = value.coerceIn(range.start, range.endInclusive)
  val valueFormatter = labels.valueFormatter
  val unit = labels.unit
  val minLabel = labels.minLabel ?: valueFormatter(range.start)
  val maxLabel = labels.maxLabel ?: valueFormatter(range.endInclusive)

  val sliderColors =
    SliderDefaults.colors(
      thumbColor = palette.ink,
      activeTrackColor = palette.action,
      activeTickColor = Color.Transparent,
      inactiveTrackColor = palette.panel3,
      inactiveTickColor = Color.Transparent,
      disabledThumbColor = palette.ink3,
      disabledActiveTrackColor = palette.panel3,
      disabledInactiveTrackColor = palette.panel3,
    )

  Column(
    modifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = labels.label, style = typography.body.copy(color = palette.ink2))
      Row(verticalAlignment = Alignment.Bottom) {
        Text(
          text = valueFormatter(clamped),
          style = typography.displayLg.copy(color = palette.actionText),
        )
        if (unit.isNotEmpty()) {
          Box(modifier = Modifier.size(width = 4.dp, height = 0.dp))
          Text(text = unit, style = typography.labelSm.copy(color = palette.ink3))
        }
      }
    }

    Slider(
      value = clamped,
      onValueChange = onValueChange,
      modifier =
        Modifier.fillMaxWidth()
          .then(if (sliderTestTag != null) Modifier.testTag(sliderTestTag) else Modifier),
      enabled = enabled,
      valueRange = range,
      colors = sliderColors,
      track = { state: SliderState ->
        SliderDefaults.Track(
          sliderState = state,
          colors = sliderColors,
          modifier = Modifier.height(4.dp),
        )
      },
      thumb = {
        Box(
          modifier =
            Modifier.size(14.dp)
              .clip(RoundedCornerShape(7.dp))
              .background(color = palette.ink, shape = RoundedCornerShape(7.dp))
              .border(width = 1.dp, color = palette.action, shape = RoundedCornerShape(7.dp))
        )
      },
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(text = minLabel, style = typography.labelSm.copy(color = palette.ink3))
      Text(text = maxLabel, style = typography.labelSm.copy(color = palette.ink3))
    }
  }
}
