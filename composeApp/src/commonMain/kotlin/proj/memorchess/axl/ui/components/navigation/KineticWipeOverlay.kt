package proj.memorchess.axl.ui.components.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.KineticMotion
import proj.memorchess.axl.ui.theme.LocalKineticPalette

/** Soft accent trail length dragged behind the bright sweep line. */
private val TRAIL_LENGTH = 96.dp

/** Width of the bright leading edge of the sweep. */
private val LINE_WIDTH = 2.dp

/**
 * Full-screen, non-interactive overlay that draws the Kinetic screen-transition signature: a bright
 * accent line that sweeps across the seam whenever the active destination changes, dragging a soft
 * glow trail behind it. The direction follows the navigation ordinal — a higher [activeIndex]
 * sweeps left-to-right, a lower one sweeps right-to-left.
 *
 * The sweep is driven by a single [Animatable] read **inside the [Canvas] draw lambda**, so the
 * effect lives entirely in the draw phase: it triggers no recomposition and nothing is drawn at all
 * while idle. Place it as the last child of the content [androidx.compose.foundation.layout.Box] so
 * it paints over the screens without intercepting input (a [Canvas] has no pointer handlers).
 *
 * @param activeIndex Ordinal of the currently active destination (the nav tab number).
 * @param modifier Modifier for the overlay; should fill the content area.
 */
@Composable
fun KineticWipeOverlay(activeIndex: Int, modifier: Modifier = Modifier) {
  val palette = LocalKineticPalette.current
  // 1f == idle (nothing drawn). A sweep runs 0f -> 1f.
  val progress = remember { Animatable(1f) }
  var forward by remember { mutableStateOf(true) }
  var previousIndex by remember { mutableStateOf(activeIndex) }

  LaunchedEffect(activeIndex) {
    if (activeIndex == previousIndex) return@LaunchedEffect
    forward = activeIndex > previousIndex
    previousIndex = activeIndex
    progress.snapTo(0f)
    progress.animateTo(1f, animationSpec = KineticMotion.travelTween())
  }

  Canvas(modifier = modifier.fillMaxSize()) {
    val p = progress.value
    if (p <= 0f || p >= 1f) return@Canvas

    // Fade the whole sweep out over the last 15% so it never pops as it reaches the far edge.
    val envelope = ((1f - p) / 0.15f).coerceIn(0f, 1f)
    val lineX = if (forward) p * size.width else (1f - p) * size.width
    val trail = TRAIL_LENGTH.toPx()
    val lineW = LINE_WIDTH.toPx()

    // Soft glow trail dragged behind the leading edge.
    val trailStart = if (forward) (lineX - trail).coerceAtLeast(0f) else lineX
    val trailWidth =
      if (forward) lineX - trailStart else (lineX + trail).coerceAtMost(size.width) - lineX
    if (trailWidth > 0f) {
      drawRect(
        brush =
          Brush.horizontalGradient(
            colors =
              if (forward) listOf(Color.Transparent, palette.accentSoft)
              else listOf(palette.accentSoft, Color.Transparent),
            startX = trailStart,
            endX = trailStart + trailWidth,
          ),
        topLeft = Offset(trailStart, 0f),
        size = Size(trailWidth, size.height),
        alpha = envelope,
      )
    }

    // Bright leading edge.
    drawRect(
      color = palette.accentGlow,
      topLeft = Offset(lineX - lineW / 2f, 0f),
      size = Size(lineW, size.height),
      alpha = envelope,
    )
  }
}
