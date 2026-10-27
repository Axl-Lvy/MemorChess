package proj.memorchess.axl.ui.components.loading

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.KineticMotion
import proj.memorchess.axl.ui.theme.LocalKineticPalette

private val RAIL_WIDTH = 160.dp

private val RAIL_HEIGHT = 2.dp

/** Fraction of the rail covered by the bright sweeping segment. */
private const val SEGMENT_FRACTION = 0.28f

private const val SWEEP_PERIOD_MS = 900

/**
 * Kinetic loading indicator: a bright segment sweeping along a thin rail, centered in its space.
 */
@Composable
fun KineticBootIndicator(modifier: Modifier = Modifier) {
  val palette = LocalKineticPalette.current
  val transition = rememberInfiniteTransition(label = "boot")
  val sweep =
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = SWEEP_PERIOD_MS, easing = KineticMotion.sweep),
          repeatMode = RepeatMode.Restart,
        ),
      label = "sweep",
    )
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.width(RAIL_WIDTH).height(RAIL_HEIGHT)) {
      val y = size.height / 2f
      val stroke = size.height
      drawLine(
        color = palette.line,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = stroke,
      )
      // Read only in the draw phase, so the sweep animates without recomposing.
      val segWidth = size.width * SEGMENT_FRACTION
      val lead = sweep.value * (size.width + segWidth) - segWidth
      val start = lead.coerceIn(0f, size.width)
      val end = (lead + segWidth).coerceIn(0f, size.width)
      if (end > start) {
        drawLine(
          color = palette.actionGlow,
          start = Offset(start, y),
          end = Offset(end, y),
          strokeWidth = stroke,
        )
      }
    }
  }
}
