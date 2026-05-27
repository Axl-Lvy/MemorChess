package proj.memorchess.axl.ui.components.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * Kinetic segmented control. Mirrors `.segs`, `.segs.two`, `.segs.three`, `.seg`, `.seg.active`,
 * and `.seg small` from `design-proposals/kinetic-base.css`.
 *
 * Renders [options] as a horizontal row of equal-width segments wrapped in a 1.dp `lineBright`
 * border over a `bg2` background, with 1.dp `lineBright` vertical dividers between segments. Each
 * segment shows a Bricolage display label (600 12sp idle, 700 12sp when active) produced by
 * [label]; if [subtext] is provided, a `monoSm` caption is rendered below the label and the
 * segment's minimum height grows from 36.dp to 44.dp.
 *
 * The currently [selected] segment is filled with `accent` and switches its content color to
 * `onAccent` (subtext at 0.7 alpha); idle segments use `ink3` for the label and `ink4` for the
 * subtext. When [enabled] is false the whole control is dimmed to 0.5 alpha and clicks are
 * suppressed. Press/hover indication is picked up from [LocalIndication] so each platform's
 * surrounding Material theme decides the ripple style.
 *
 * @param T value type backing each segment — typically an enum or sealed subclass.
 * @param options the segments to render, in display order.
 * @param selected the currently selected value (must be present in [options] for a segment to
 *   highlight).
 * @param onSelect invoked with the chosen value when the user taps a segment.
 * @param modifier external modifier applied to the outer container.
 * @param label maps a value to its main display label.
 * @param subtext optional mapping from a value to a small uppercase mono caption shown beneath the
 *   label; pass `null` to omit the caption row entirely.
 * @param enabled when false, clicks are disabled and the control is rendered at 0.5 alpha.
 */
@Composable
fun <T> KineticSegmentedControl(
  options: List<T>,
  selected: T,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  label: (T) -> String,
  subtext: ((T) -> String)? = null,
  enabled: Boolean = true,
) {
  val palette = LocalKineticPalette.current
  val hasSubtext = subtext != null
  val rowHeight = if (hasSubtext) 44.dp else 36.dp

  Row(
    modifier =
      modifier
        .height(rowHeight)
        .alpha(if (enabled) 1f else 0.5f)
        .background(color = palette.bg2)
        .border(BorderStroke(1.dp, palette.lineBright)),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    options.forEachIndexed { index, option ->
      KineticSegment(
        label = label(option),
        subtext = subtext?.invoke(option),
        isActive = option == selected,
        enabled = enabled,
        onClick = { onSelect(option) },
      )

      if (index < options.lastIndex) {
        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(palette.lineBright))
      }
    }
  }
}

/**
 * One segment of a [KineticSegmentedControl]. Extracted so the active/idle styling branches don't
 * inflate the parent's cognitive complexity.
 *
 * @param label main display label.
 * @param subtext optional uppercase mono caption shown beneath the label.
 * @param isActive whether this segment is the selected one.
 * @param enabled whether clicks are accepted.
 * @param onClick invoked when the segment is tapped.
 */
@Composable
private fun RowScope.KineticSegment(
  label: String,
  subtext: String?,
  isActive: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val indication = LocalIndication.current
  val background = if (isActive) palette.accent else Color.Transparent
  val labelColor = if (isActive) palette.onAccent else palette.ink3
  val subtextColor = if (isActive) palette.onAccent.copy(alpha = 0.7f) else palette.ink4
  val labelWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold
  val interactionSource = remember { MutableInteractionSource() }

  Box(
    modifier =
      Modifier.fillMaxHeight()
        .weight(1f)
        .background(color = background)
        .clickable(
          interactionSource = interactionSource,
          indication = indication,
          enabled = enabled,
          role = Role.RadioButton,
          onClick = onClick,
        )
        .padding(horizontal = 8.dp, vertical = 9.dp),
    contentAlignment = Alignment.Center,
  ) {
    CompositionLocalProvider(
      LocalContentColor provides labelColor,
      LocalTextStyle provides
        typography.display.copy(fontSize = 12.sp, fontWeight = labelWeight, color = labelColor),
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(text = label)
        if (subtext != null) {
          Text(text = subtext.uppercase(), style = typography.monoSm.copy(color = subtextColor))
        }
      }
    }
  }
}
