package proj.memorchess.axl.ui.components.training

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.LocalKineticPalette

private val RAIL_HEIGHT = 10.dp

private val RAIL_CORNER_RADIUS = 6.dp

/**
 * Horizontal progress rail with a fill and a glowing marker, also reported to accessibility.
 * Rounding is drawn rather than clipped, since the marker's halo overflows the rail.
 *
 * @param progress Clamped into `0f..1f`; `NaN` and infinities count as `0f`.
 */
@Composable
fun KineticProgressRail(progress: Float, modifier: Modifier = Modifier) {
  val palette = LocalKineticPalette.current
  val safeProgress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f

  val progressColor = palette.progress
  val progressGlow = palette.progressGlow
  val panel3 = palette.panel3

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(RAIL_HEIGHT)
        .semantics {
          progressBarRangeInfo = ProgressBarRangeInfo(current = safeProgress, range = 0f..1f)
        }
        .drawBehind {
          val railWidth = size.width
          val railHeight = size.height
          val markerX = railWidth * safeProgress
          val radiusPx = RAIL_CORNER_RADIUS.toPx()
          val trackRadius = minOf(radiusPx, railWidth / 2f, railHeight / 2f)

          drawRoundRect(
            color = panel3,
            topLeft = Offset.Zero,
            size = Size(railWidth, railHeight),
            cornerRadius = CornerRadius(trackRadius),
          )

          // A zero-width rect still draws a one-pixel sliver on some backends.
          if (safeProgress > 0f) {
            val fillWidth = railWidth * safeProgress
            val fillRadius = minOf(radiusPx, fillWidth / 2f, railHeight / 2f)
            drawRoundRect(
              color = progressColor,
              topLeft = Offset.Zero,
              size = Size(fillWidth, railHeight),
              cornerRadius = CornerRadius(fillRadius),
            )
          }

          // The marker is drawn even when empty, so the rail always shows a head.
          val centerY = railHeight / 2f
          val glowRadiusPx = 6.dp.toPx()
          val markerRadiusPx = 4.dp.toPx()

          drawCircle(
            color = progressGlow.copy(alpha = 0.45f),
            radius = glowRadiusPx,
            center = Offset(markerX, centerY),
          )
          drawCircle(
            color = progressColor,
            radius = markerRadiusPx,
            center = Offset(markerX, centerY),
          )
        }
  )
}
