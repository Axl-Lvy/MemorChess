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

private val FLASH_STROKE = 3.dp

/**
 * Flashes a decaying border after each graded move, `progress` when [success] and `destructive`
 * otherwise. The alpha is read in the draw phase, so the decay never recomposes.
 *
 * @param attempt Monotonic counter of graded moves; each new positive value triggers a flash.
 */
fun Modifier.registeredFlash(attempt: Int, success: Boolean): Modifier = composed {
  val palette = LocalKineticPalette.current
  val alpha = remember { Animatable(0f) }
  LaunchedEffect(attempt) {
    if (attempt > 0) {
      alpha.snapTo(1f)
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
  val color = if (success) palette.progress else palette.destructive
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
