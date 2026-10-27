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

/** Brand mark: a left-leaning parallelogram with an upright "M" centered inside. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 36.dp) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val letter =
    typography.displayLg.copy(color = palette.onAction, fontSize = (size.value * 0.61f).sp)
  Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.size(size)) {
      val w = this.size.width
      val h = this.size.height
      // Shifting X by -tan(6°) * y makes the bottom edge lean left.
      val dx = (tan(SKEW_DEGREES * PI / 180.0).toFloat()) * h
      val path =
        Path().apply {
          moveTo(0f, 0f)
          lineTo(w, 0f)
          lineTo(w - dx, h)
          lineTo(-dx, h)
          close()
        }
      drawPath(path = path, color = palette.action)
      drawPath(path = path, color = palette.actionGlow, style = Stroke(width = 1f))
    }
    Text(text = "M", style = letter)
  }
}
