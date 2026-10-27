package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import proj.memorchess.axl.ui.theme.KineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * Clamps [whiteRatio] into `0f..1f` with zero as `+0.0f`. Non-finite input reads as balanced
 * (`0.5f`), unlike [proj.memorchess.axl.ui.components.training.KineticProgressRail]'s `0f`.
 */
internal fun evalRailSafeRatio(whiteRatio: Float): Float {
  if (whiteRatio.isNaN() || whiteRatio.isInfinite()) return 0.5f
  val coerced = whiteRatio.coerceIn(0f, 1f)
  // coerceIn keeps -0.0f, so normalise it.
  return if (coerced == 0f) 0f else coerced
}

/** Pixel band of the parity marker, always inside `0f..railHeightPx`, even for a shorter rail. */
internal fun evalRailMarkerBand(
  railHeightPx: Float,
  markerThicknessPx: Float,
  safeRatio: Float,
): ClosedFloatingPointRange<Float> {
  // Shrunk to fit: coerceIn throws on the inverted range a 0 height measure pass would give.
  val half = minOf(markerThicknessPx / 2f, railHeightPx / 2f)
  val centre = (safeRatio * railHeightPx).coerceIn(half, railHeightPx - half)
  return (centre - half)..(centre + half)
}

/** Colour of the eval rail's parity marker, `progress` so it separates from `action` in dark. */
internal fun kineticEvalMarkerColor(palette: KineticPalette): Color = palette.progress

/**
 * Vertical evaluation rail splitting white (top) from black (bottom) at a parity marker, with an
 * optional [displayValue] such as `"+0.4"` or `"M3"` below it.
 *
 * @param whiteRatio White's share of the rail, clamped into `0f..1f`; non-finite reads as `0.5f`.
 */
@Composable
fun KineticEvalRail(
  whiteRatio: Float,
  displayValue: String? = null,
  modifier: Modifier = Modifier,
  thin: Boolean = false,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current

  val safeRatio = evalRailSafeRatio(whiteRatio)

  val railWidth = if (thin) 14.dp else 18.dp
  val railShape = MaterialTheme.shapes.extraSmall
  val sqLight = palette.sqLight
  val sqDark = palette.sqDark
  val markerColor = kineticEvalMarkerColor(palette)
  val markerGlow = palette.progressGlow.copy(alpha = 0.5f)

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Box(
      modifier =
        Modifier.width(railWidth)
          .weight(1f)
          // Clip first, or the section rects square off the corners.
          .clip(railShape)
          .background(palette.panel, railShape)
          .drawBehind {
            val w = size.width
            val h = size.height
            val markerThickness = 2.dp.toPx()
            val markerBand = evalRailMarkerBand(h, markerThickness, safeRatio)
            val markerCenter = (markerBand.start + markerBand.endInclusive) / 2f
            val whiteHeight = safeRatio * h

            if (whiteHeight > 0f) {
              drawRect(color = sqLight, topLeft = Offset.Zero, size = Size(w, whiteHeight))
            }
            val blackHeight = h - whiteHeight
            if (blackHeight > 0f) {
              drawRect(
                color = sqDark,
                topLeft = Offset(0f, whiteHeight),
                size = Size(w, blackHeight),
              )
            }
            val glowThickness = 8.dp.toPx()
            val glowTop = (markerCenter - glowThickness / 2f).coerceAtLeast(0f)
            val glowBottom = (markerCenter + glowThickness / 2f).coerceAtMost(h)
            drawRect(
              color = markerGlow,
              topLeft = Offset(0f, glowTop),
              size = Size(w, glowBottom - glowTop),
            )
            drawRect(
              color = markerColor,
              topLeft = Offset(0f, markerBand.start),
              size = Size(w, markerBand.endInclusive - markerBand.start),
            )
          }
          .border(1.5.dp, palette.line, railShape)
    ) {}

    if (displayValue != null) {
      Text(
        text = displayValue,
        style = typography.displaySm.copy(fontSize = 12.sp, color = palette.progressText),
        modifier = Modifier.padding(top = 2.dp),
      )
    }
  }
}
