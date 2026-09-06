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
 * Clamps [whiteRatio] into the rail's renderable range: every non-finite input (`NaN`, `±Infinity`)
 * falls back to the `0.5f` midpoint, anything else is coerced into `0f..1f`.
 *
 * The fallback is deliberately *not* the `0f` used by
 * [proj.memorchess.axl.ui.components.training.KineticProgressRail]: a missing evaluation reads as
 * "balanced", not as "black wins". Do not harmonise the two.
 *
 * A coerced `-0.0f` survives `coerceIn` untouched (IEEE says `-0.0f < 0.0f` is false), so the
 * result is normalised to a canonical `+0.0f`. Nothing on screen moves — `-0.0f * height` is still
 * zero and `whiteHeight > 0f` is false either way — but the boundary becomes assertable without
 * depending on how a matcher treats signed zero.
 *
 * @param whiteRatio Raw ratio supplied by the caller.
 * @return A finite ratio inside `0f..1f`, with zero in its canonical positive form.
 */
internal fun evalRailSafeRatio(whiteRatio: Float): Float {
  if (whiteRatio.isNaN() || whiteRatio.isInfinite()) return 0.5f
  val coerced = whiteRatio.coerceIn(0f, 1f)
  return if (coerced == 0f) 0f else coerced
}

/**
 * Vertical span the crisp parity marker occupies, in pixels, guaranteed to satisfy `0f <= start <=
 * endInclusive <= railHeightPx` for every input — including a rail shorter than the marker itself,
 * where the half-thickness is shrunk to fit rather than fed to `coerceIn` with an inverted range
 * (`Float.coerceIn` throws on `min > max`, which a 0-height measure pass reaches).
 *
 * @param railHeightPx Measured rail height in pixels.
 * @param markerThicknessPx Desired marker thickness in pixels.
 * @param safeRatio Ratio already normalised by [evalRailSafeRatio].
 * @return The marker's `top..bottom` band, `0f..0f` for a zero-height rail.
 */
internal fun evalRailMarkerBand(
  railHeightPx: Float,
  markerThicknessPx: Float,
  safeRatio: Float,
): ClosedFloatingPointRange<Float> {
  val half = minOf(markerThicknessPx / 2f, railHeightPx / 2f)
  val centre = (safeRatio * railHeightPx).coerceIn(half, railHeightPx - half)
  return (centre - half)..(centre + half)
}

/**
 * Colour of the eval rail's parity marker: the `progress` role, lime in dark and violet in light.
 *
 * In light this deliberately coincides with `palette.action` — the documented light-palette role
 * collision — and the two separate by hue only in dark, which is the whole point of routing the
 * marker through `progress` rather than `action`.
 *
 * @param palette Palette to resolve the role against.
 * @return The marker colour for [palette].
 */
internal fun kineticEvalMarkerColor(palette: KineticPalette): Color = palette.progress

/**
 * Kinetic evaluation rail. Renders a vertical rail showing the balance between white (top,
 * `palette.sqLight`) and black (bottom, `palette.sqDark`), with a 2.dp marker and a soft glow at
 * the parity point and an optional [displayValue] text (e.g. `"+0.4"` or `"M3"`) below it.
 *
 * The marker rides the `progress` role through its own seam ([kineticEvalMarkerColor]); its glow
 * (`palette.progressGlow`, drawn at half alpha) and the value text (`palette.progressText`) read
 * the role directly, so the tick is lime in dark and violet in light with no per-theme branching.
 *
 * Shape: the rail clips to `MaterialTheme.shapes.extraSmall` (12.dp, the chip / in-card bucket) so
 * the white and black section rects cannot paint over the corners, and carries a 1.5.dp
 * `palette.line` stroke on that same shape. At the rail's 14-18.dp width a 12.dp radius exceeds
 * half the width, so `RoundedCornerShape` clamps it to roughly half the rail's width (7-9.dp) and
 * the rail reads as a pill — which is what the artboards draw.
 *
 * Clamping behaviour lives in [evalRailSafeRatio] and the marker geometry in [evalRailMarkerBand]:
 * `NaN` and infinite ratios fall back to `0.5f`, everything else is coerced into `0f..1f` with
 * `-0.0f` normalised to `+0.0f`, and the marker always ends up fully inside the rail even when the
 * rail measures shorter than the marker. That guarantee is in layout coordinates only: at the
 * extremes (`whiteRatio` `0f`/`1f`) the marker centres on the pill's rounded cap rather than its
 * flat side, so it is mostly hidden behind the clip and reads as a thin sliver rather than a full
 * 2.dp tick. That is accepted as by-design — extreme evaluations still read (the tick is visible,
 * just capped), and the pill shape is what the artboards specify.
 *
 * @param whiteRatio Position of the parity marker. `0f` = black has the entire rail, `1f` = white
 *   has the entire rail. Values outside `0f..1f` are clamped; `NaN`/`Infinity` fall back to `0.5f`.
 * @param displayValue Optional evaluation text rendered beneath the rail (Baloo 2 700 12sp, in
 *   `palette.progressText`).
 * @param modifier External modifier applied to the rail column.
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
          // Clip before drawBehind — otherwise the section rects square off the 12.dp corners.
          .clip(railShape)
          .background(palette.panel, railShape)
          .drawBehind {
            val w = size.width
            val h = size.height
            val markerThickness = 2.dp.toPx()
            val markerBand = evalRailMarkerBand(h, markerThickness, safeRatio)
            val markerCenter = (markerBand.start + markerBand.endInclusive) / 2f
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
            // Soft glow — wider semi-transparent progress band behind the crisp marker.
            val glowThickness = 8.dp.toPx()
            val glowTop = (markerCenter - glowThickness / 2f).coerceAtLeast(0f)
            val glowBottom = (markerCenter + glowThickness / 2f).coerceAtMost(h)
            drawRect(
              color = markerGlow,
              topLeft = Offset(0f, glowTop),
              size = Size(w, glowBottom - glowTop),
            )
            // Crisp marker.
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
