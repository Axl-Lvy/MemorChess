package proj.memorchess.axl.ui.components.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.tan
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

private const val SKEW_DEGREES = 6f

/**
 * Kinetic brand mark — a left-leaning orange parallelogram with an upright "M" centered inside.
 *
 * Mirrors `.brand` from `design-proposals/kinetic-base.css`: a 36×36 accent square skewed `-6°` on
 * the X axis, with an inset 1px accent-glow border and a counter-skewed "M" letter in Bricolage
 * 800. Because the inner letter counter-skews back to upright, this implementation draws the
 *      parallelogram directly (no `graphicsLayer` skew needed) and centers an upright [Text].
 *
 * The outer soft glow from CSS (`box-shadow: 0 0 18px`) is intentionally omitted in v0 — Compose
 * Multiplatform's cross-target blur story is uneven, and the smoke-test goal is to verify fonts +
 * colors render on all four targets, not to perfectly reproduce the glow.
 *
 * @param size Mark size; defaults to the 36.dp used in the desktop top bar.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 36.dp) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val letter =
    typography.displayLg.copy(color = palette.onAccent, fontSize = (size.value * 0.61f).sp)
  Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.size(size)) {
      val w = this.size.width
      val h = this.size.height
      // skewX(-6deg) shifts each point's X by -tan(6°) * y → bottom edge leans left.
      val dx = (tan(SKEW_DEGREES * PI / 180.0).toFloat()) * h
      val path =
        Path().apply {
          moveTo(0f, 0f)
          lineTo(w, 0f)
          lineTo(w - dx, h)
          lineTo(-dx, h)
          close()
        }
      drawPath(path = path, color = palette.accent)
      drawPath(path = path, color = palette.accentGlow, style = Stroke(width = 1f))
    }
    Text(text = "M", style = letter)
  }
}
