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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography
import proj.memorchess.axl.ui.theme.kineticShadow

/**
 * Chrome around the chess board: a shadowed panel with an accent stripe, an optional corner tag and
 * an optional your-move pill. The caller applies any `aspectRatio(1f)` to [modifier].
 *
 * @param cornerTag Uppercase label punched through the top-left border.
 * @param cornerTagAccent Suffix appended to [cornerTag] in the action text colour.
 * @param cornerTagColor [cornerTag] text colour, `ink3` when `null`.
 * @param yourMovePill Action pill at the top centre.
 */
@Composable
fun KineticBoardShell(
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  cornerTag: String? = null,
  cornerTagAccent: String? = null,
  cornerTagColor: Color? = null,
  yourMovePill: String? = null,
  content: @Composable () -> Unit,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current

  val innerPadding = if (compact) 10.dp else 18.dp
  val stripeWidth = if (compact) 40.dp else 60.dp
  val stripeHeight = if (compact) 3.dp else 4.dp
  val streak = palette.streak
  val action = palette.action
  val streakGlow = palette.streakGlow.copy(alpha = 0.4f)

  Box(modifier = modifier, contentAlignment = Alignment.TopStart) {
    Box(
      modifier =
        Modifier.matchParentSize()
          .kineticShadow(big = !compact)
          .background(palette.panel)
          .drawWithContent {
            drawContent()
            val stripeWpx = stripeWidth.toPx()
            val stripeHpx = stripeHeight.toPx()
            // Flush right, straddling the panel's top edge.
            val x = size.width - stripeWpx
            val y = -stripeHpx / 2f
            val glowExtra = 4.dp.toPx()
            drawRect(
              color = streakGlow,
              topLeft = Offset(x - glowExtra, y - glowExtra),
              size = Size(stripeWpx + glowExtra * 2f, stripeHpx + glowExtra * 2f),
            )
            drawRect(
              brush =
                Brush.horizontalGradient(
                  colorStops = arrayOf(0f to streak, 1f to action),
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
            style =
              typography.labelSm.copy(fontSize = 9.5.sp, color = cornerTagColor ?: palette.ink3),
          )
          if (cornerTagAccent != null) {
            Text(
              text = " " + cornerTagAccent.uppercase(),
              style = typography.labelSm.copy(fontSize = 9.5.sp, color = palette.actionText),
            )
          }
        }
      }
    }

    if (yourMovePill != null) {
      Box(
        modifier =
          Modifier.align(Alignment.TopCenter)
            .offset(y = (-16).dp)
            .background(palette.action)
            .padding(horizontal = 12.dp, vertical = 4.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = yourMovePill.uppercase(),
            style = typography.displaySm.copy(fontSize = 11.sp, color = palette.onAction),
          )
          Text(
            text = " ▼",
            style = typography.displaySm.copy(fontSize = 9.sp, color = palette.onAction),
          )
        }
      }
    }
  }
}
