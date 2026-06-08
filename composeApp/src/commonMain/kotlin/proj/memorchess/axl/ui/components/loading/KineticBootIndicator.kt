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

/** Width of the boot rail. */
private val RAIL_WIDTH = 160.dp

/** Thickness of the boot rail line. */
private val RAIL_HEIGHT = 2.dp

/** Fraction of the rail covered by the bright sweeping segment. */
private const val SEGMENT_FRACTION = 0.28f

/** One full traversal of the sweep, in milliseconds. */
private const val SWEEP_PERIOD_MS = 900

/**
 * Kinetic "power-on" loading indicator: a hard-edged accent segment that sweeps along a thin rail,
 * replacing the generic circular spinner with something true to the instrument aesthetic.
 *
 * The sweep is an infinite transition whose value is read **inside the [Canvas] draw lambda**, so
 * it animates entirely in the draw phase without recomposing. Centered in the available space.
 *
 * @param modifier Modifier for the indicator; fills its space and centers the rail.
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
      // Bright segment travels left to right and wraps off the far edge.
      val segWidth = size.width * SEGMENT_FRACTION
      val lead = sweep.value * (size.width + segWidth) - segWidth
      val start = lead.coerceIn(0f, size.width)
      val end = (lead + segWidth).coerceIn(0f, size.width)
      if (end > start) {
        drawLine(
          color = palette.accentGlow,
          start = Offset(start, y),
          end = Offset(end, y),
          strokeWidth = stroke,
        )
      }
    }
  }
}
