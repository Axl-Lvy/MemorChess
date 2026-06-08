package proj.memorchess.axl.ui.components.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.KineticMotion
import proj.memorchess.axl.ui.theme.LocalKineticPalette

/** Thickness of the flashed border. */
private val FLASH_STROKE = 3.dp

/**
 * Draws a one-shot "registered" feedback border around the content: a bright [green][palette] frame
 * on a correct move, [red][palette] on a wrong one, that pulses on and decays. Mirrors the Kinetic
 * instrument metaphor — the board *registers* the verdict rather than fading a banner in.
 *
 * The pulse fires whenever [attempt] changes to a new positive value (each graded move bumps the
 * attempt counter), using [success] to pick the colour. The alpha is held in an [Animatable] read
 * **inside [drawWithContent]**, so the decay runs in the draw phase with no recomposition, and
 * nothing is drawn once it settles back to zero.
 *
 * @param attempt Monotonic counter of graded moves; a change triggers a flash.
 * @param success Whether the move that produced this [attempt] was correct.
 */
fun Modifier.registeredFlash(attempt: Int, success: Boolean): Modifier = composed {
  val palette = LocalKineticPalette.current
  val alpha = remember { Animatable(0f) }
  LaunchedEffect(attempt) {
    if (attempt > 0) {
      alpha.snapTo(1f)
      // Fires instantly (snapTo) then decays steadily — a clean glow falloff, not a snap-out.
      alpha.animateTo(
        targetValue = 0f,
        animationSpec =
          tween(
            durationMillis = KineticMotion.register.inWholeMilliseconds.toInt(),
            easing = KineticMotion.sweep,
          ),
      )
    }
  }
  val color = if (success) palette.green else palette.red
  drawWithContent {
    drawContent()
    val a = alpha.value
    if (a > 0f) {
      val stroke = FLASH_STROKE.toPx()
      drawRect(
        color = color,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        style = Stroke(width = stroke),
        alpha = a,
      )
    }
  }
}
