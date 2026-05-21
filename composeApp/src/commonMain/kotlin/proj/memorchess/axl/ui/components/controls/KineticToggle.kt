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
 * Kinetic toggle switch. Mirrors `.toggle` and `.toggle.on` from
 * `design-proposals/kinetic-base.css`.
 *
 * A 44×24.dp pill with an 18×18.dp thumb that slides between the left (off) and right (on) edges.
 * Off state uses `panel3` background with a `lineBright` border and an `ink3` thumb; on state
 * switches to an `accent` background and border with an `onAccent` thumb. The thumb's horizontal
 * offset is animated via [animateDpAsState] with the default spring spec.
 *
 * Picks up [LocalIndication] for press/hover indication so it inherits whatever the surrounding
 * Material theme provides on each target.
 *
 * @param checked whether the toggle is currently in the "on" state.
 * @param onCheckedChange invoked with the new state when the user toggles the control.
 * @param modifier external modifier applied to the toggle pill.
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

  val background = if (checked) palette.accent else palette.panel3
  val borderColor = if (checked) palette.accent else palette.lineBright
  val thumbColor = if (checked) palette.onAccent else palette.ink3

  // Off: 2.dp from left. On: 22.dp from left (i.e. 2.dp from the right of the 44.dp pill).
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
