package proj.memorchess.axl.ui.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * Kinetic slider control. Mirrors `.slider`, `.slider-row`, `.slider .track`, `.slider .fill`,
 * `.slider .thumb`, `.slider .label-min`, `.slider .label-max`, and `.slider-row .value` from
 * `design-proposals/kinetic-base.css`.
 *
 * Layout is a three row [Column] with 8.dp spacing between rows:
 * - Row 1: [label] on the left in the Kinetic body style ([LocalKineticTypography]'s `body`, color
 *   `ink2`) and the formatted value on the right in `displayLg` (Bricolage 800 24sp, color
 *   `accentText`). When [unit] is non empty it is appended in `monoSm` style with color `ink3`.
 * - Row 2: a 4.dp tall track in `panel3` with an `accent` colored fill from 0 to the current value,
 *   topped by a 14×14.dp `RoundedCornerShape(7.dp)` thumb filled with `ink` and bordered 1.dp in
 *   `accent`. The track and fill are rendered manually so the visual matches the CSS exactly; the
 *   Material 3 [Slider] is overlaid (transparent track and thumb) to handle pointer input, gesture
 *   semantics and accessibility.
 * - Row 3: [minLabel] left and [maxLabel] right in `monoSm` style, color `ink3`.
 *
 * When [enabled] is false the whole control is drawn at 50% alpha and the underlying [Slider] is
 * also disabled.
 *
 * @param value current slider value, clamped into [range] for visual rendering.
 * @param onValueChange called whenever the user drags the slider.
 * @param modifier external modifier applied to the root column.
 * @param range valid value range, defaults to `0f..1f`.
 * @param label descriptive label for the slider, shown top-left.
 * @param valueFormatter formats [value] for the big readout and default min/max labels.
 * @param unit optional unit shown after the value (e.g. "ms"); rendered in `monoSm` `ink3`.
 * @param minLabel small mono label under the left edge of the track.
 * @param maxLabel small mono label under the right edge of the track.
 * @param enabled when false, dims the control and disables interaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KineticSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  range: ClosedFloatingPointRange<Float> = 0f..1f,
  label: String,
  valueFormatter: (Float) -> String = { it.toString() },
  unit: String = "",
  minLabel: String = valueFormatter(range.start),
  maxLabel: String = valueFormatter(range.endInclusive),
  enabled: Boolean = true,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current

  // Clamp value into the range for visual rendering; the Slider itself enforces the same bounds.
  val clamped = value.coerceIn(range.start, range.endInclusive)
  val span = range.endInclusive - range.start
  val fraction = if (span > 0f) (clamped - range.start) / span else 0f

  Column(
    modifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Row 1: label + value readout.
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = label, style = typography.body.copy(color = palette.ink2))
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

    // Row 2: track + fill + thumb overlaid with a transparent Material 3 Slider for input.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(14.dp)) {
      val trackWidth = maxWidth
      val fillWidth = trackWidth * fraction
      // Track (panel3).
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(4.dp)
            .align(Alignment.Center)
            .background(color = palette.panel3)
      )
      // Fill (accent).
      Box(
        modifier =
          Modifier.size(width = fillWidth, height = 4.dp)
            .align(Alignment.CenterStart)
            .background(color = palette.accent)
      )
      // Material 3 Slider overlay — fully transparent, drives input and a11y.
      Slider(
        value = clamped,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().align(Alignment.Center),
        enabled = enabled,
        valueRange = range,
        track = { _: SliderState ->
          // Material's track is suppressed; we paint our own behind the overlay.
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
    }

    // Row 3: min/max labels.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(text = minLabel, style = typography.monoSm.copy(color = palette.ink3))
      Text(text = maxLabel, style = typography.monoSm.copy(color = palette.ink3))
    }
  }
}
