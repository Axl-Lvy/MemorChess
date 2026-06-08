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
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.KineticMotion
import proj.memorchess.axl.ui.theme.LocalKineticPalette

/** Soft accent trail length dragged behind the bright sweep line. */
private val TRAIL_LENGTH = 120.dp

/** Width of the bright leading edge of the sweep. */
private val LINE_WIDTH = 2.dp

/** Number of stepped bands approximating the trail's fade (solid rects, no per-frame shader). */
private const val TRAIL_STEPS = 5

/**
 * Full-screen, non-interactive overlay that draws the Kinetic screen-transition signature: a bright
 * accent line that sweeps across the screen whenever the active destination changes, dragging a
 * soft stepped glow trail behind it. The direction follows the navigation ordinal — a higher
 * [activeIndex] sweeps left-to-right, a lower one sweeps right-to-left.
 *
 * This sweep **is** the screen transition: the `NavHost` swap underneath is instant (see
 * [proj.memorchess.axl.ui.pages.navigation.Router]) so only one screen is ever composed, and the
 * motion lives here instead. It is driven by a single [Animatable] read **inside the [Canvas] draw
 * lambda**, so the effect runs entirely in the draw phase: no recomposition, no per-frame
 * allocation, and nothing is drawn at all while idle. Place it as the last child of the content
 * [androidx.compose.foundation.layout.Box] so it paints over the screens without intercepting input
 * (a [Canvas] has no pointer handlers).
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
    progress.animateTo(1f, animationSpec = KineticMotion.sweepTween())
  }

  Canvas(modifier = modifier.fillMaxSize()) {
    val p = progress.value
    if (p <= 0f || p >= 1f) return@Canvas

    // Fade the whole sweep out over the last 20% so it never pops as it reaches the far edge.
    val envelope = ((1f - p) / 0.2f).coerceIn(0f, 1f)
    val lineX = if (forward) p * size.width else (1f - p) * size.width
    val trail = TRAIL_LENGTH.toPx()
    val step = trail / TRAIL_STEPS
    val direction = if (forward) -1f else 1f // trail extends opposite the travel direction

    // Stepped translucent bands fading away from the leading edge — cheap stand-in for a gradient.
    for (i in 0 until TRAIL_STEPS) {
      val near = lineX + direction * step * i
      val far = lineX + direction * step * (i + 1)
      val left = minOf(near, far).coerceIn(0f, size.width)
      val right = maxOf(near, far).coerceIn(0f, size.width)
      if (right > left) {
        val bandAlpha = envelope * (1f - i.toFloat() / TRAIL_STEPS)
        drawRect(
          color = palette.accentSoft,
          topLeft = Offset(left, 0f),
          size = Size(right - left, size.height),
          alpha = bandAlpha,
        )
      }
    }

    // Bright leading edge.
    val lineW = LINE_WIDTH.toPx()
    drawRect(
      color = palette.accentGlow,
      topLeft = Offset(lineX - lineW / 2f, 0f),
      size = Size(lineW, size.height),
      alpha = envelope,
    )
  }
}
