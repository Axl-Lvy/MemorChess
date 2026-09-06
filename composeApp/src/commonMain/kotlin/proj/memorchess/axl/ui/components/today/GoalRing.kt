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

/**
 * Outer diameter of the ring.
 *
 * NOT read off an artboard: screens `1a`/`1g`/`1m` of the design canvas
 * (`claude.ai/design/p/db4f236e-b602-4b5f-bcbb-a4cf70525664`) could not be reached from this
 * sandboxed environment (no authenticated browser session, no design-system project access — see
 * the commit body for every access path attempted). Chosen to read as a hero-sized dashboard
 * centerpiece; needs a pass against the real artboard before merge.
 */
private val RING_DIAMETER = 120.dp

/**
 * Stroke width of both the track and the progress arc.
 *
 * Reused verbatim from [proj.memorchess.axl.ui.components.training.KineticProgressRail]'s
 * `RAIL_HEIGHT` (10.dp) rather than an artboard read (see [RING_DIAMETER]'s note), so the ring's
 * line weight stays consistent with the rail's.
 */
private val RING_STROKE_WIDTH = 10.dp

/**
 * Radius of the soft glow halo at the progress tip.
 *
 * Reused verbatim from [proj.memorchess.axl.ui.components.training.KineticProgressRail]'s marker
 * glow (a 12.dp diameter halo) rather than an artboard read (see [RING_DIAMETER]'s note).
 */
private val TIP_GLOW_RADIUS = 6.dp

/**
 * Radius of the solid dot at the progress tip.
 *
 * Reused verbatim from [proj.memorchess.axl.ui.components.training.KineticProgressRail]'s marker
 * dot (an 8.dp diameter dot) rather than an artboard read (see [RING_DIAMETER]'s note).
 */
private val TIP_DOT_RADIUS = 4.dp

/**
 * Kinetic circular goal ring.
 *
 * A [RING_DIAMETER] circle used on the Today page to show today's training goal completion. Renders
 * three layers via [Modifier.drawBehind], mirroring
 * [proj.memorchess.axl.ui.components.training.KineticProgressRail]'s track/fill/marker structure
 * adapted from a bar to a ring:
 * 1. A full [panel3][proj.memorchess.axl.ui.theme.KineticPalette.panel3] track circle, stroked with
 *    round caps.
 * 2. A clockwise [progress][proj.memorchess.axl.ui.theme.KineticPalette.progress] arc starting at
 *    12 o'clock (`-90` degrees) and sweeping `360 * progress` degrees. Only drawn when the clamped
 *    progress is strictly greater than `0f`, to avoid a zero-length arc artifact — same rationale
 *    as the rail's zero-width fill guard.
 * 3. A soft [progressGlow][proj.memorchess.axl.ui.theme.KineticPalette.progressGlow] halo behind a
 *    solid [progress][proj.memorchess.axl.ui.theme.KineticPalette.progress] dot, positioned at the
 *    arc's current tip. Always drawn, even at `0f` (where the tip sits at 12 o'clock), so the ring
 *    always shows a head — exactly like the rail's marker.
 *
 * The [progress] parameter is clamped into `0f..1f`. Non-finite values (`NaN`, `+∞`, `-∞`) fall
 * back to `0f`. The clamped value is also reported to accessibility services as a
 * [ProgressBarRangeInfo] over `0f..1f`.
 *
 * No center label slot: like [proj.memorchess.axl.ui.components.training.KineticProgressRail], this
 * stays presentation only. The caller overlays its own text centered in a [Box] around it.
 *
 * @param progress Goal completion in `[0f, 1f]`. Values outside this range are clamped; `NaN` and
 *   infinities are treated as `0f`.
 * @param modifier Modifier applied to the outer [RING_DIAMETER] square container.
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

          // 1. Track.
          drawArc(
            color = panel3,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
          )

          // 2. Progress arc — only when there is something to draw, otherwise a zero-length arc
          //    would still risk a backend-dependent artifact (same rationale as the rail's
          //    zero-width fill guard).
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

          // 3. Tip glow + dot. Always drawn (also at progress == 0), so the ring always shows a
          //    head — at 12 o'clock when empty, back at 12 o'clock when full.
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
