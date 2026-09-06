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

/** Height of the rail, matching the 10px rail in artboard `1l`. */
private val RAIL_HEIGHT = 10.dp

/** Corner radius of both the track and the fill, matching `1l`'s 6px. */
private val RAIL_CORNER_RADIUS = 6.dp

/**
 * Kinetic horizontal progress rail.
 *
 * A 10.dp tall rail used between the Training counters and the moves trail. Renders three layers
 * via [Modifier.drawBehind] on a transparent container that is allowed to draw outside its bounds:
 * 1. A full-width [panel3][proj.memorchess.axl.ui.theme.KineticPalette.panel3] track, rounded to
 *    6.dp.
 * 2. A left-aligned fill from `x = 0` to `progress * width`, rounded to the same 6.dp and painted
 *    with a flat [progress][proj.memorchess.axl.ui.theme.KineticPalette.progress] color — no
 *    gradient. Only drawn when the clamped progress is strictly greater than 0 to avoid a
 *    zero-width rectangle, and its radius is additionally clamped to half the fill width so a
 *    sliver-thin fill cannot produce a backend-dependent artifact.
 * 3. A 12.dp soft [progressGlow][proj.memorchess.axl.ui.theme.KineticPalette.progressGlow] halo
 *    behind an 8.dp [progress][proj.memorchess.axl.ui.theme.KineticPalette.progress] marker,
 *    centred on the rail at `x = progress * width`. The 8.dp marker sits inside the rail; only the
 *    halo overflows, by 1.dp above and 1.dp below. That overflow is why the rounding is drawn with
 *    `drawRoundRect` rather than a [Modifier.clip][androidx.compose.ui.draw.clip], which would cut
 *    the halo off.
 *
 * The [progress] parameter is clamped into `0f..1f`. Non-finite values (`NaN`, `+∞`, `-∞`) fall
 * back to `0f` — no fill is drawn and the marker sits at the left edge. This guards against bad
 * inputs from upstream counters (e.g. `0 / 0` when there is nothing to train yet).
 *
 * The clamped value is also reported to accessibility services as a [ProgressBarRangeInfo] over
 * `0f..1f`, so the rail is no longer invisible to screen readers.
 *
 * @param progress Training completion in `[0f, 1f]`. Values outside this range are clamped; `NaN`
 *   and infinities are treated as `0f`.
 * @param modifier Modifier applied to the outer 10.dp tall, full-width container.
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

          // 1. Track.
          drawRoundRect(
            color = panel3,
            topLeft = Offset.Zero,
            size = Size(railWidth, railHeight),
            cornerRadius = CornerRadius(trackRadius),
          )

          // 2. Fill — only when there is something to draw, otherwise a zero-width rect would still
          //    create a one-pixel sliver on some backends. The radius is clamped to half the fill
          //    width so a fill narrower than the corner diameter stays well-formed.
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

          // 3. Marker glow + marker. Always drawn (also at progress == 0), so the rail always shows
          //    a head — at the very left when empty, at the very right when full.
          val centerY = railHeight / 2f
          val glowRadiusPx = 6.dp.toPx() // 12.dp diameter halo
          val markerRadiusPx = 4.dp.toPx() // 8.dp diameter dot

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
