package proj.memorchess.axl.ui.components.controls

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * Row of equal-width segments, one per option, highlighting [selected]. Segments grow together to
 * fit a wrapped label rather than clipping it.
 *
 * @param subtext caption shown under each label; `null` omits the caption row.
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
  val containerShape = RoundedCornerShape(14.dp)

  Row(
    modifier =
      modifier
        // Lets every segment's fillMaxHeight() match the tallest, wrapped one instead of clipping.
        .height(IntrinsicSize.Min)
        .heightIn(min = rowHeight)
        .alpha(if (enabled) 1f else 0.5f)
        .clip(containerShape)
        .background(color = palette.bg2, shape = containerShape)
        .padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    options.forEach { option ->
      KineticSegment(
        label = label(option),
        subtext = subtext?.invoke(option),
        isActive = option == selected,
        enabled = enabled,
        onClick = { onSelect(option) },
      )
    }
  }
}

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
  val background = if (isActive) palette.action else Color.Transparent
  val labelColor = if (isActive) palette.onAction else palette.ink3
  val subtextColor = if (isActive) palette.onAction.copy(alpha = 0.7f) else palette.ink4
  val labelWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold
  val interactionSource = remember { MutableInteractionSource() }
  // Own shape rather than the container's clip, so a middle selection still renders rounded.
  val segmentShape = RoundedCornerShape(11.dp)

  Box(
    modifier =
      Modifier.fillMaxHeight()
        .weight(1f)
        .clip(segmentShape)
        .background(color = background, shape = segmentShape)
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
          Text(text = subtext.uppercase(), style = typography.labelSm.copy(color = subtextColor))
        }
      }
    }
  }
}
