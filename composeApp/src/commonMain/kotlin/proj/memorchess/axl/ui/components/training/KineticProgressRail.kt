package proj.memorchess.axl.ui.components.training

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.LocalKineticPalette

/**
 * Kinetic horizontal progress rail.
 *
 * A thin 2.dp tall rail used between the Training counters and the moves trail. Renders three
 * layers via [Modifier.drawBehind] on a transparent container that is allowed to draw outside its
 * 2.dp bounds (the marker overflows above and below):
 * 1. A full-width [panel3][proj.memorchess.axl.ui.theme.KineticPalette.panel3] background bar.
 * 2. A left-aligned fill rectangle from `x = 0` to `progress * width`, painted with a horizontal
 *    [accent][proj.memorchess.axl.ui.theme.KineticPalette.accent] →
 *    [accentGlow][proj.memorchess.axl.ui.theme.KineticPalette.accentGlow] gradient. Only drawn when
 *    the clamped progress is strictly greater than 0 to avoid a zero-width rectangle.
 * 3. A 12.dp soft [accentGlow][proj.memorchess.axl.ui.theme.KineticPalette.accentGlow] halo behind
 *    an 8.dp [accent][proj.memorchess.axl.ui.theme.KineticPalette.accent] marker, centred on the
 *    rail at `x = progress * width`.
 *
 * The [progress] parameter is clamped into `0f..1f`. Non-finite values (`NaN`, `+∞`, `-∞`) fall
 * back to `0f` — no fill is drawn and the marker sits at the left edge. This guards against bad
 * inputs from upstream counters (e.g. `0 / 0` when there is nothing to train yet).
 *
 * @param progress Training completion in `[0f, 1f]`. Values outside this range are clamped; `NaN`
 *   and infinities are treated as `0f`.
 * @param modifier Modifier applied to the outer 2.dp tall, full-width container.
 */
@Composable
fun KineticProgressRail(progress: Float, modifier: Modifier = Modifier) {
  val palette = LocalKineticPalette.current
  val safeProgress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f

  val accent = palette.accent
  val accentGlow = palette.accentGlow
  val panel3 = palette.panel3

  Box(
    modifier =
      modifier.fillMaxWidth().height(2.dp).drawBehind {
        val railWidth = size.width
        val railHeight = size.height
        val markerX = railWidth * safeProgress

        // 1. Background bar.
        drawRect(color = panel3, topLeft = Offset.Zero, size = Size(railWidth, railHeight))

        // 2. Fill — only when there is something to draw, otherwise a zero-width rect would still
        //    create a one-pixel sliver of gradient on some backends.
        if (safeProgress > 0f) {
          val fillWidth = railWidth * safeProgress
          drawRect(
            brush =
              Brush.horizontalGradient(
                colors = listOf(accent, accentGlow),
                startX = 0f,
                endX = railWidth,
              ),
            topLeft = Offset.Zero,
            size = Size(fillWidth, railHeight),
          )
        }

        // 3. Marker glow + marker. Always drawn (also at progress == 0), so the rail always shows
        //    a head — at the very left when empty, at the very right when full.
        val centerY = railHeight / 2f
        val glowRadiusPx = 6.dp.toPx() // 12.dp diameter halo
        val markerRadiusPx = 4.dp.toPx() // 8.dp diameter dot

        drawCircle(
          color = accentGlow.copy(alpha = 0.45f),
          radius = glowRadiusPx,
          center = Offset(markerX, centerY),
        )
        drawCircle(color = accent, radius = markerRadiusPx, center = Offset(markerX, centerY))
      }
  )
}
