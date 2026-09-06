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

/** Opacity of the light-theme hard shadow block at the small 5.dp offset. */
private const val LIGHT_SHADOW_ALPHA = 0.08f

/** Opacity of the light-theme hard shadow block at the big 12.dp offset. */
private const val LIGHT_SHADOW_ALPHA_BIG = 0.06f

/**
 * Kinetic hard-offset shadow. Mirrors `--shadow-hard` / `--shadow-big` from `kinetic-base.css`: a
 * flat offset block (no blur) plus a 1.dp outline.
 *
 * In dark mode the offset block uses [KineticPalette.bg2]; in light mode it uses a translucent wash
 * of [KineticPalette.ink], which is itself violet-black, so the block reads as a shadow on the
 * lavender page instead of tinting it. (It used to be cyan, which suited the pre-retune ice-blue
 * base and clashes with the current violet one.)
 *
 * @param big When true uses the larger 12.dp offset (board shells, modals); otherwise 5.dp.
 * @param shape Outline the offset block (and, when [drawBorder] is true, the 1.dp line stroke)
 *   follow. Defaults to [RectangleShape], reproducing the old square-only behaviour so
 *   [KineticBoardShell] and [KineticDialog] — which pass neither this nor [drawBorder] — render
 *   pixel-identical to before this parameter existed.
 * @param drawBorder When false, suppresses this modifier's own 1.dp `line` stroke. Set this for a
 *   caller whose own `.border(...)` must be the single visible stroke on the surface — chaining
 *   both would paint this one on top, covering the caller's.
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
 * The opaque edge color for [kineticPressableElevation], resolved from [palette]. Uses the bright
 * line token so the affordance reads as a solid edge in both themes instead of a translucent tint.
 *
 * This is deliberately one neutral edge for every pressable, not a darker shade of each button's
 * own fill the way the mockup draws it (lime over `#7DC612`, violet over `#4C1D95`, pink over
 * `#A10D50`). Per-fill edges need palette tokens that do not exist yet — no current token is darker
 * than `action` or `destructive` in *both* themes — so they belong to the button-style sweep, not
 * here. The known cost until then: in light the edge (`#C9BCE8`) is lighter than a `Primary` or
 * `Danger` face, so it reads as a highlight rather than a shadow.
 */
internal fun kineticPressableEdgeColor(palette: KineticPalette): Color = palette.lineBright

/**
 * Kinetic chunky pressable-button elevation for a button clipped to [shape]. At rest the button
 * sits 4.dp above a hard bottom edge. On [pressed] it translates 3.dp down and the edge collapses
 * to a 1.dp sliver, so the button's base stays flush across both states.
 *
 * The style is static. Nothing here animates between the two states.
 *
 * Chain any pointer input modifier such as `clickable` before this one and `background(...)` after
 * it. The press translate is a layout offset, so a `clickable` placed after this modifier moves
 * down with the button while the pointer stays put. That cancels a press near the top edge. The
 * edge is drawn behind whatever follows, so a `background` placed before this modifier gets painted
 * over by the edge instead of covering it.
 *
 * [outline] is the button's own stroke and is drawn by this modifier so it travels with the press
 * offset, and so exactly one stroke ends up on the face: a border chained after this modifier would
 * be painted over by the one drawn here. Pass `null` for a face with no outline.
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
