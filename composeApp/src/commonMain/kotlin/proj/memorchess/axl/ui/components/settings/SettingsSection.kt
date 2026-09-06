package proj.memorchess.axl.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography
import proj.memorchess.axl.ui.theme.kineticShadow

/**
 * Kinetic settings section container. Mirrors `.section`, `.section-h`, and `.card.full` from
 * `design-proposals/kinetic-settings-desktop.html`.
 *
 * Renders a `panel` background clipped and outlined to a 20.dp rounded
 * [MaterialTheme.shapes.medium] card, with a 1.5.dp `line` border and a 5.dp Kinetic offset shadow
 * following the same rounded outline. The card's own border is the single visible stroke on the
 * card — [kineticShadow] is asked not to draw its own line stroke (`drawBorder = false`), since
 * chained after this card's border it would otherwise paint over it. The [title] sits at the top in
 * Baloo 2 700 16sp `ink`, with an optional [description] line beneath in `labelSm` `ink3`. A 12.dp
 * gap separates the title block from [content].
 *
 * When [danger] is true the section gains a 3.dp `destructive` vertical strip on the left edge
 * (drawn via [drawBehind], clipped to the card's rounded outline) and the background is tinted with
 * `destructiveSoft`; the border switches to `destructiveDim`. Use this for the Danger Zone section
 * only.
 *
 * @param title Uppercase-ready section heading, rendered as-is.
 * @param modifier External modifier applied to the outer column.
 * @param description Optional secondary line shown directly under the title.
 * @param danger When true, applies the danger styling (destructive strip + tinted background).
 * @param content Section body, rendered in a [Column] with no inter-child spacing — callers compose
 *   their own spacing.
 */
@Composable
fun SettingsSection(
  title: String,
  modifier: Modifier = Modifier,
  description: String? = null,
  danger: Boolean = false,
  content: @Composable ColumnScope.() -> Unit,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val background = if (danger) palette.destructiveSoft else palette.panel
  val borderColor = if (danger) palette.destructiveDim else palette.line
  val titleColor = if (danger) palette.destructive else palette.ink
  val strip = palette.destructive
  val cardShape = MaterialTheme.shapes.medium // 20.dp, per SHAPE CONTRACT "Cards / panels / sheets"

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .kineticShadow(big = false, shape = cardShape, drawBorder = false)
        .clip(cardShape)
        .background(color = background, shape = cardShape)
        .border(width = 1.5.dp, color = borderColor, shape = cardShape)
        .then(
          if (danger) {
            Modifier.drawBehind {
              val stripPx = 3.dp.toPx()
              drawRect(color = strip, topLeft = Offset(0f, 0f), size = Size(stripPx, size.height))
            }
          } else {
            Modifier
          }
        )
        .padding(
          PaddingValues(
            start = if (danger) 21.dp else 18.dp,
            top = 18.dp,
            end = 18.dp,
            bottom = 18.dp,
          )
        )
  ) {
    Text(text = title, style = typography.display.copy(color = titleColor))
    if (description != null) {
      Text(text = description, style = typography.labelSm.copy(color = palette.ink3))
    }
    Column(
      modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      content = content,
    )
  }
}
