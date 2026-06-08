package proj.memorchess.axl.ui.components.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.KineticMotion
import proj.memorchess.axl.ui.theme.LocalKineticPalette

/** Width of the bright accent seam between the two panels. */
private val SEAM_WIDTH = 2.dp

/**
 * Two-panel curtain wipe for a `NavHost` destination. During a navigation both the outgoing and
 * incoming screens are composed at once (kept alive by [KineticMotion.holdEnter] / [holdExit]);
 * this modifier clips each to its side of a vertical accent seam that travels across the screen, so
 * the old screen and the new screen are visible **simultaneously**, split by the moving line.
 *
 * Both panels stay static — only the clip boundary moves — which keeps the per-frame cost to two
 * clipped layer composites plus the seam rect. The wipe progress is taken from the destination's
 * own enter/exit [transition] and read **inside [drawWithContent]**, so the clip animates in the
 * draw phase with no recomposition. The incoming panel paints the bright seam (unclipped, on top)
 * so it is drawn exactly once.
 *
 * Apply inside a `composable<…> { }` block, where the receiver is the [AnimatedVisibilityScope].
 *
 * @param revealFromRight When `true` the incoming screen grows in from the right edge (seam travels
 *   right-to-left); when `false` it grows in from the left. Driven by navigation direction.
 */
@Composable
fun AnimatedVisibilityScope.wipeReveal(revealFromRight: Boolean): Modifier {
  val seamColor = LocalKineticPalette.current.accentGlow
  val entering = transition.targetState == EnterExitState.Visible
  val progress =
    transition.animateFloat(transitionSpec = { KineticMotion.sweepTween() }, label = "wipe") {
      if (it == EnterExitState.Visible) 1f else 0f
    }
  return Modifier.drawWithContent {
    val raw = progress.value
    // Normalize so both panels share one 0 -> 1 wipe progress.
    val p = if (entering) raw else 1f - raw
    val w = size.width
    // Seam x. Reveal-from-right means the seam starts at the right edge and travels left.
    val x = if (revealFromRight) (1f - p) * w else p * w
    // The new (entering) screen occupies the already-revealed side; the old one the remainder.
    val newOnLeft = !revealFromRight
    val left: Float
    val right: Float
    if (entering == newOnLeft) {
      left = 0f
      right = x
    } else {
      left = x
      right = w
    }
    clipRect(left = left, top = 0f, right = right, bottom = size.height) {
      this@drawWithContent.drawContent()
    }
    if (entering && raw > 0f && raw < 1f) {
      val lineWidth = SEAM_WIDTH.toPx()
      drawRect(
        color = seamColor,
        topLeft = Offset(x - lineWidth / 2f, 0f),
        size = Size(lineWidth, size.height),
      )
    }
  }
}
