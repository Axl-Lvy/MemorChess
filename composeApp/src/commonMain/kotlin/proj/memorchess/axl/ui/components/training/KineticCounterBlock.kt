package proj.memorchess.axl.ui.components.training

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * Visual tone of a [KineticCounterBlock].
 *
 * The tone controls the color of the 3.dp left accent stripe. The numeric value itself always uses
 * the [ink] color so the block reads as a neutral stat card rather than a colored badge.
 */
enum class KineticCounterTone {
  /** Green left border — success / correct count. */
  Success,
  /** Red left border — failure / incorrect count. */
  Fail,
  /** Accent (orange) left border — generic stats such as "Left". */
  Neutral,
}

/**
 * Small numeric stat card used on the Training page.
 *
 * Mirrors the `.counter` rule from `design-proposals/kinetic-base.css`: panel background, 1.dp
 * `line` border on top/right/bottom, and a 3.dp colored stripe on the left whose color is selected
 * by [tone]. Inside, a small uppercase mono [label] sits above a big Bricolage [value].
 *
 * The component never sets its own width; the caller is expected to provide it through [modifier]
 * (typically `Modifier.weight(1f)` inside a 3-cell Row). When called without any width modifier the
 * block falls back to [Modifier.wrapContentWidth] so an unconstrained call still renders.
 *
 * The [value] is rendered via [Int.toString] without thousand separators, so it gracefully handles
 * the full Int range including [Int.MAX_VALUE] and negative values (with a leading minus sign).
 */
@Composable
fun KineticCounterBlock(
  label: String,
  value: Int,
  tone: KineticCounterTone,
  modifier: Modifier = Modifier,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val stripeColor =
    when (tone) {
      KineticCounterTone.Success -> palette.green
      KineticCounterTone.Fail -> palette.red
      KineticCounterTone.Neutral -> palette.accent
    }

  Column(
    modifier =
      modifier
        .background(palette.panel)
        .border(1.dp, palette.line)
        .drawBehind {
          val stripePx = 3.dp.toPx()
          drawRect(
            color = stripeColor,
            topLeft = Offset(0f, 0f),
            size = Size(stripePx, size.height),
          )
        }
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(text = label.uppercase(), style = typography.monoSm.copy(color = palette.ink3))
    Text(text = value.toString(), style = typography.displayLg.copy(color = palette.ink))
  }
}
