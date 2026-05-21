package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography
import proj.memorchess.axl.ui.theme.kineticShadow

/**
 * Kinetic board shell. Mirrors `.board-shell`, `.board-shell.compact`, `.board-shell::after`,
 * `.board-shell .corner-tag`, and `.board-shell .your-move` from
 * `design-proposals/kinetic-base.css` — the chrome around the chess board.
 *
 * Renders four pieces of chrome around a [content] slot:
 * - **Panel + border + hard-offset shadow** via [Modifier.kineticShadow] (big in normal mode, small
 *   in [compact] mode). Inner padding is 18.dp normally, 10.dp in compact mode.
 * - **Accent stripe** — a 60.dp×4.dp (or 40.dp×3.dp when [compact]) cyan→orange horizontal gradient
 *   straddling the top-right edge of the panel, with a soft cyan glow behind it. Drawn via
 *   [Modifier.drawWithContent] so it sits on top of the panel border but behind the absolute
 *   children (tag, pill).
 * - **Corner tag** — when [cornerTag] is non-null, a small mono uppercase label punched through the
 *   top-left border (offset by 12.dp from the left, -7.dp vertically). An optional
 *   [cornerTagAccent] suffix is appended in `accentText` colour.
 * - **Your-move pill** — when [yourMovePill] is non-null, an accent-coloured pill positioned at the
 *   top-centre sliding up out of the panel by ~16.dp, with a small downward caret glyph.
 *
 * The shell is just chrome — [content] receives the entire inner area (board grid). The caller is
 * responsible for any `aspectRatio(1f)` constraint on the outer [modifier].
 *
 * @param modifier Outer modifier applied to the shell.
 * @param compact When `true`, uses the tighter padding/shadow/stripe variants.
 * @param cornerTag Optional uppercase label punched through the top-left border (e.g. `"EXPLORE ·
 *   italian/quiet"`).
 * @param cornerTagAccent Optional accent suffix appended to [cornerTag] in `accentText` colour
 *   (e.g. a move count like `"12"`).
 * @param yourMovePill Optional accent pill at the top-centre (e.g. `"YOUR MOVE"`).
 * @param content The inner board area (typically a board grid).
 */
@Composable
fun KineticBoardShell(
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  cornerTag: String? = null,
  cornerTagAccent: String? = null,
  yourMovePill: String? = null,
  content: @Composable () -> Unit,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current

  val innerPadding = if (compact) 10.dp else 18.dp
  val stripeWidth = if (compact) 40.dp else 60.dp
  val stripeHeight = if (compact) 3.dp else 4.dp
  val cyan = palette.cyan
  val accent = palette.accent
  val cyanGlow = palette.cyanGlow

  Box(modifier = modifier, contentAlignment = Alignment.TopStart) {
    // Panel with shadow + border + accent stripe.
    Box(
      modifier =
        Modifier.matchParentSize()
          .kineticShadow(big = !compact)
          .background(palette.panel)
          .drawWithContent {
            drawContent()
            val stripeWpx = stripeWidth.toPx()
            val stripeHpx = stripeHeight.toPx()
            // Position: top-right, straddling the top edge of the panel border.
            val x = size.width - stripeWpx
            val y = -stripeHpx / 2f
            // Soft glow behind the crisp stripe.
            val glowExtra = 4.dp.toPx()
            drawRect(
              color = cyanGlow,
              topLeft = Offset(x - glowExtra, y - glowExtra),
              size = Size(stripeWpx + glowExtra * 2f, stripeHpx + glowExtra * 2f),
            )
            // Crisp cyan→accent gradient stripe.
            drawRect(
              brush =
                Brush.horizontalGradient(
                  colorStops = arrayOf(0f to cyan, 1f to accent),
                  startX = x,
                  endX = x + stripeWpx,
                ),
              topLeft = Offset(x, y),
              size = Size(stripeWpx, stripeHpx),
            )
          }
          .padding(innerPadding)
    ) {
      content()
    }

    // Corner tag — punched through the top-left border, half-outside.
    if (cornerTag != null) {
      Box(
        modifier =
          Modifier.align(Alignment.TopStart)
            .offset(x = 12.dp, y = (-7).dp)
            .background(palette.panel)
            .padding(horizontal = 4.dp, vertical = 2.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = cornerTag.uppercase(),
            style = typography.monoSm.copy(fontSize = 9.5.sp, color = palette.ink3),
          )
          if (cornerTagAccent != null) {
            Text(
              text = " " + cornerTagAccent.uppercase(),
              style = typography.monoSm.copy(fontSize = 9.5.sp, color = palette.accentText),
            )
          }
        }
      }
    }

    // Your-move pill — slides up out of the top edge by ~16.dp.
    if (yourMovePill != null) {
      Box(
        modifier =
          Modifier.align(Alignment.TopCenter)
            .offset(y = (-16).dp)
            .background(palette.accent)
            .padding(horizontal = 12.dp, vertical = 4.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = yourMovePill.uppercase(),
            style = typography.displaySm.copy(fontSize = 11.sp, color = palette.onAccent),
          )
          Text(
            text = " ▼",
            style = typography.displaySm.copy(fontSize = 9.sp, color = palette.onAccent),
          )
        }
      }
    }
  }
}
