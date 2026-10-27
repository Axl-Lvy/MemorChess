package proj.memorchess.axl.ui.components.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import proj.memorchess.axl.ui.theme.LocalKineticPalette

private val RING_DIAMETER = 120.dp

/** Matches [proj.memorchess.axl.ui.components.training.KineticProgressRail]'s height. */
private val RING_STROKE_WIDTH = 10.dp

/** Matches [proj.memorchess.axl.ui.components.training.KineticProgressRail]'s marker glow. */
private val TIP_GLOW_RADIUS = 6.dp

/** Matches [proj.memorchess.axl.ui.components.training.KineticProgressRail]'s marker dot. */
private val TIP_DOT_RADIUS = 4.dp

/**
 * Circular goal ring: a clockwise progress arc from 12 o'clock with a dot at its tip.
 *
 * @param progress Goal completion, clamped into `0f..1f`; `NaN` and infinities count as `0f`.
 */
@Composable
fun GoalRing(progress: Float, modifier: Modifier = Modifier) {
  val palette = LocalKineticPalette.current
  val safeProgress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f

  val progressColor = palette.progress
  val progressGlow = palette.progressGlow
  val panel3 = palette.panel3

  Box(
    modifier =
      modifier
        .size(RING_DIAMETER)
        .semantics {
          progressBarRangeInfo = ProgressBarRangeInfo(current = safeProgress, range = 0f..1f)
        }
        .drawBehind {
          val strokePx = RING_STROKE_WIDTH.toPx()
          val diameter = size.minDimension - strokePx
          val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
          val arcSize = Size(diameter, diameter)
          val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)

          drawArc(
            color = panel3,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
          )

          // A zero length arc can leave a backend dependent artifact.
          if (safeProgress > 0f) {
            drawArc(
              color = progressColor,
              startAngle = -90f,
              sweepAngle = 360f * safeProgress,
              useCenter = false,
              topLeft = topLeft,
              size = arcSize,
              style = stroke,
            )
          }

          // The tip is drawn even at 0, so the ring always shows a head.
          val tipAngleDegrees = -90f + 360f * safeProgress
          val tipAngleRadians = tipAngleDegrees * (kotlin.math.PI.toFloat() / 180f)
          val radius = diameter / 2f
          val center = Offset(size.width / 2f, size.height / 2f)
          val tip =
            Offset(
              center.x + radius * cos(tipAngleRadians),
              center.y + radius * sin(tipAngleRadians),
            )

          drawCircle(
            color = progressGlow.copy(alpha = 0.45f),
            radius = TIP_GLOW_RADIUS.toPx(),
            center = tip,
          )
          drawCircle(color = progressColor, radius = TIP_DOT_RADIUS.toPx(), center = tip)
        }
  )
}
