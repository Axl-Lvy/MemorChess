package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * Kinetic evaluation rail. Mirrors `.eval-rail`, `.eval-rail .w`, `.eval-rail .b`, `.eval-rail
 * .marker`, and `.eval-rail .v` from `design-proposals/kinetic-base.css`.
 *
 * Renders a vertical rail showing the balance between white (top, `palette.sqLight`) and black
 * (bottom, `palette.sqDark`). A 2.dp accent-coloured marker with a soft glow is drawn at the parity
 * point, and an optional [displayValue] text (e.g. `"+0.4"` or `"M3"`) is rendered below the rail.
 *
 * Clamping behaviour: [whiteRatio] is clamped into `0f..1f` before rendering. `NaN` and infinite
 * values are treated as `0.5f` (midpoint fallback) — these inputs never crash or paint outside the
 * container. `0f` paints an all-black rail with the marker at the very top; `1f` paints an
 * all-white rail with the marker at the very bottom; `0.5f` paints the marker at the midpoint.
 *
 * @param whiteRatio Position of the parity marker. `0f` = black has the entire rail, `1f` = white
 *   has the entire rail. Values outside `0f..1f` are clamped; `NaN`/`Infinity` fall back to `0.5f`.
 * @param displayValue Optional evaluation text rendered beneath the rail (Bricolage 700 12sp,
 *   accent colour).
 * @param thin When `true`, the rail is 14.dp wide instead of the default 18.dp.
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

  val safeRatio =
    if (whiteRatio.isNaN() || whiteRatio.isInfinite()) {
      0.5f
    } else {
      whiteRatio.coerceIn(0f, 1f)
    }

  val railWidth = if (thin) 14.dp else 18.dp
  val sqLight = palette.sqLight
  val sqDark = palette.sqDark
  val accent = palette.accent
  val accentGlow = palette.accentGlow.copy(alpha = 0.5f)

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Box(
      modifier =
        Modifier.width(railWidth)
          .weight(1f)
          .background(palette.panel)
          .border(1.dp, palette.line)
          .drawBehind {
            val w = size.width
            val h = size.height
            // Marker geometry — 2.dp thick, centred on safeRatio * h, clamped fully inside.
            val markerThickness = 2.dp.toPx()
            val rawCenter = safeRatio * h
            val markerCenter = rawCenter.coerceIn(markerThickness / 2f, h - markerThickness / 2f)
            val whiteHeight = safeRatio * h

            // White section (top).
            if (whiteHeight > 0f) {
              drawRect(color = sqLight, topLeft = Offset.Zero, size = Size(w, whiteHeight))
            }
            // Black section (bottom).
            val blackHeight = h - whiteHeight
            if (blackHeight > 0f) {
              drawRect(
                color = sqDark,
                topLeft = Offset(0f, whiteHeight),
                size = Size(w, blackHeight),
              )
            }
            // Soft glow — wider semi-transparent accent band behind the crisp marker.
            val glowThickness = 8.dp.toPx()
            val glowTop = (markerCenter - glowThickness / 2f).coerceAtLeast(0f)
            val glowBottom = (markerCenter + glowThickness / 2f).coerceAtMost(h)
            drawRect(
              color = accentGlow,
              topLeft = Offset(0f, glowTop),
              size = Size(w, glowBottom - glowTop),
            )
            // Crisp marker.
            val markerTop = (markerCenter - markerThickness / 2f).coerceAtLeast(0f)
            val markerBottom = (markerCenter + markerThickness / 2f).coerceAtMost(h)
            drawRect(
              color = accent,
              topLeft = Offset(0f, markerTop),
              size = Size(w, markerBottom - markerTop),
            )
          }
    ) {}

    if (displayValue != null) {
      Text(
        text = displayValue,
        style = typography.displaySm.copy(fontSize = 12.sp, color = palette.accentText),
        modifier = Modifier.padding(top = 2.dp),
      )
    }
  }
}
