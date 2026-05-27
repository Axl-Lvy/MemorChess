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
 * @property label descriptive label for the slider, shown top-left.
 * @property valueFormatter formats the value for the big readout and default min/max labels.
 * @property unit optional unit shown after the value (e.g. "ms"); rendered in `monoSm` `ink3`.
 * @property minLabel small mono label under the left edge; defaults to the formatted range start.
 * @property maxLabel small mono label under the right edge; defaults to the formatted range end.
 */
data class KineticSliderLabels(
  val label: String,
  val valueFormatter: (Float) -> String = { it.toString() },
  val unit: String = "",
  val minLabel: String? = null,
  val maxLabel: String? = null,
)

/**
 * Kinetic slider control. Mirrors `.slider`, `.slider .track`, `.slider .fill`, `.slider .thumb`,
 * `.slider .label-min`, `.slider .label-max`, and `.slider-row .value` from
 * `design-proposals/kinetic-base.css`.
 *
 * Layout is a three-row [Column] with 8.dp spacing:
 * - Row 1: [label] on the left in `body` (`ink2`); formatted value on the right in `displayLg`
 *   (`accentText`). When [unit] is non empty it is appended in `monoSm` (`ink3`).
 * - Row 2: a Material 3 [Slider] with a custom track ([SliderDefaults.Track] re-colored to use
 *   Kinetic tokens — `accent` for the active fill and `panel3` for the inactive remainder) and a
 *   custom 14×14.dp thumb (`ink` fill, 1.dp `accent` border).
 * - Row 3: [minLabel] left and [maxLabel] right in `monoSm` (`ink3`).
 *
 * Earlier revisions tried to overlay a transparent Material Slider on top of a hand-painted track;
 * that broke gesture pickup on touch screens because the track lambda returned no composable
 * content for the gesture region to attach to. This implementation lets the M3 Slider own its
 * native gesture handling and skins it via [SliderDefaults.colors] instead.
 *
 * @param value current slider value, clamped into [range].
 * @param onValueChange called whenever the user drags the slider.
 * @param labels text and formatting configuration (label, value formatter, unit, edge labels).
 * @param modifier external modifier applied to the root column.
 * @param range valid value range, defaults to `0f..1f`.
 * @param enabled when false, dims the control and disables interaction.
 * @param sliderTestTag optional test tag attached to the slider input region.
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
      activeTrackColor = palette.accent,
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
          style = typography.displayLg.copy(color = palette.accentText),
        )
        if (unit.isNotEmpty()) {
          Box(modifier = Modifier.size(width = 4.dp, height = 0.dp))
          Text(text = unit, style = typography.monoSm.copy(color = palette.ink3))
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
              .border(width = 1.dp, color = palette.accent, shape = RoundedCornerShape(7.dp))
        )
      },
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(text = minLabel, style = typography.monoSm.copy(color = palette.ink3))
      Text(text = maxLabel, style = typography.monoSm.copy(color = palette.ink3))
    }
  }
}
