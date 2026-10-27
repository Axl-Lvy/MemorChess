package proj.memorchess.axl.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val LIGHT_SHADOW_ALPHA = 0.08f

private const val LIGHT_SHADOW_ALPHA_BIG = 0.06f

/**
 * Kinetic hard-offset shadow: a flat offset block with no blur, plus a 1.dp outline.
 *
 * @param big Uses a 12.dp offset instead of 5.dp.
 * @param drawBorder When false, omits the outline so a caller's own `.border(...)` stays the only
 *   visible stroke.
 */
@Composable
fun Modifier.kineticShadow(
  big: Boolean = false,
  shape: Shape = RectangleShape,
  drawBorder: Boolean = true,
): Modifier = composed {
  val palette = LocalKineticPalette.current
  val offset: Dp = if (big) 12.dp else 5.dp
  val shadowColor: Color =
    if (palette.isLight) {
      palette.ink.copy(alpha = if (big) LIGHT_SHADOW_ALPHA_BIG else LIGHT_SHADOW_ALPHA)
    } else {
      palette.bg2
    }
  this.drawBehind {
      val o = offset.toPx()
      translate(left = o, top = o) {
        drawOutline(shape.createOutline(size, layoutDirection, this), color = shadowColor)
      }
    }
    .then(
      if (drawBorder) Modifier.border(width = 1.dp, color = palette.line, shape = shape)
      else Modifier
    )
}

/**
 * Opaque edge color for [kineticPressableElevation], one neutral edge for every pressable since no
 * token is darker than every button fill in both themes.
 */
internal fun kineticPressableEdgeColor(palette: KineticPalette): Color = palette.lineBright

/**
 * Chunky pressable elevation for a button clipped to [shape]: it rests 4.dp above a hard edge and
 * drops 3.dp on [pressed], without animating.
 *
 * Chain pointer input such as `clickable` before this modifier and `background(...)` after it: the
 * press is a layout offset that would cancel presses near the top edge, and the edge paints over an
 * earlier background.
 *
 * @param outline The button's stroke, drawn here so it moves with the press; `null` for none.
 */
@Composable
internal fun Modifier.kineticPressableElevation(
  pressed: Boolean,
  shape: Shape,
  outline: BorderStroke?,
): Modifier = composed {
  val palette = LocalKineticPalette.current
  val restOffset: Dp = 4.dp
  val pressedOffset: Dp = 1.dp
  val edgeOffset: Dp = if (pressed) pressedOffset else restOffset
  val shadowColor: Color = kineticPressableEdgeColor(palette)
  this.offset(y = if (pressed) 3.dp else 0.dp)
    .drawBehind {
      translate(top = edgeOffset.toPx()) {
        drawOutline(shape.createOutline(size, layoutDirection, this), color = shadowColor)
      }
    }
    .then(if (outline == null) Modifier else Modifier.border(outline, shape))
}
