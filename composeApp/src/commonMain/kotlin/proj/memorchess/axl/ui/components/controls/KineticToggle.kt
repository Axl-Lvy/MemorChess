package proj.memorchess.axl.ui.components.controls

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.LocalKineticPalette

/**
 * Kinetic toggle switch: a pill whose thumb slides left when off and right when on.
 *
 * @param enabled when false, the control is dimmed to 50% alpha and clicks are ignored.
 */
@Composable
fun KineticToggle(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val palette = LocalKineticPalette.current
  val interactionSource = remember { MutableInteractionSource() }
  val indication = LocalIndication.current

  val containerShape = RoundedCornerShape(12.dp)
  val thumbShape = RoundedCornerShape(9.dp)

  val background = if (checked) palette.action else palette.panel3
  val borderColor = if (checked) palette.action else palette.lineBright
  val thumbColor = if (checked) palette.onAction else palette.ink3

  // 2.dp inset from either edge of the 44.dp pill.
  val targetOffset = if (checked) 22.dp else 2.dp
  val thumbOffset by animateDpAsState(targetValue = targetOffset)

  Box(
    modifier =
      modifier
        .width(44.dp)
        .height(24.dp)
        .alpha(if (enabled) 1f else 0.5f)
        .clip(containerShape)
        .background(color = background, shape = containerShape)
        .border(width = 1.dp, color = borderColor, shape = containerShape)
        .toggleable(
          value = checked,
          interactionSource = interactionSource,
          indication = indication,
          enabled = enabled,
          role = Role.Switch,
          onValueChange = onCheckedChange,
        ),
    contentAlignment = Alignment.CenterStart,
  ) {
    Box(
      modifier =
        Modifier.offset(x = thumbOffset)
          .size(18.dp)
          .clip(thumbShape)
          .background(color = thumbColor, shape = thumbShape)
    )
  }
}
