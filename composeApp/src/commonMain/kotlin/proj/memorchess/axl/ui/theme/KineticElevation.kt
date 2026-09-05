package proj.memorchess.axl.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Kinetic hard-offset shadow. Mirrors `--shadow-hard` / `--shadow-big` from `kinetic-base.css`: a
 * flat offset block (no blur) plus a 1.dp outline.
 *
 * In dark mode the offset block uses [KineticPalette.bg2]; in light mode it uses a cyan-tinted
 * translucent shadow so the palette stays cool/futuristic and never grey.
 *
 * @param big When true uses the larger 12.dp offset (board shells, modals); otherwise 5.dp.
 */
@Composable
fun Modifier.kineticShadow(big: Boolean = false): Modifier = composed {
  val palette = LocalKineticPalette.current
  val offset: Dp = if (big) 12.dp else 5.dp
  val shadowColor: Color =
    if (palette.isLight) {
      // Light: cyan-tinted (rgba(0, 184, 212, 0.08) for hard, 0.06 for big)
      if (big) Color(0x0F00B8D4) else Color(0x1400B8D4)
    } else {
      palette.bg2
    }
  this.drawBehind {
      val o = offset.toPx()
      drawRect(color = shadowColor, topLeft = Offset(o, o), size = Size(size.width, size.height))
    }
    .border(width = 1.dp, color = palette.line)
}

/**
 * Kinetic chunky pressable-button elevation for a button clipped to [shape]. At rest the button
 * sits 4.dp above a hard bottom edge. On [pressed] it translates 3.dp down and the edge collapses
 * to a 1.dp sliver, so the button's base stays flush across both states.
 *
 * The style is static. Nothing here animates between the two states.
 */
@Composable
internal fun Modifier.kineticPressableElevation(
  pressed: Boolean,
  shape: Shape = MaterialTheme.shapes.medium,
): Modifier = composed {
  val palette = LocalKineticPalette.current
  val restOffset: Dp = 4.dp
  val pressedOffset: Dp = 1.dp
  val edgeOffset: Dp = if (pressed) pressedOffset else restOffset
  val shadowColor: Color = if (palette.isLight) Color(0x1400B8D4) else palette.bg2
  this.offset(y = if (pressed) 3.dp else 0.dp)
    .drawBehind {
      translate(top = edgeOffset.toPx()) {
        drawOutline(shape.createOutline(size, layoutDirection, this), color = shadowColor)
      }
    }
    .border(width = 1.dp, color = palette.line, shape = shape)
}
