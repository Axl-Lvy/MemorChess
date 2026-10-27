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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography
import proj.memorchess.axl.ui.theme.kineticShadow

/**
 * Settings section card with a [title], an optional [description], and [content]. [danger] adds the
 * destructive strip and tint reserved for the Danger Zone.
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
  val cardShape = MaterialTheme.shapes.medium

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .kineticShadow(big = false, shape = cardShape, drawBorder = false)
        .clip(cardShape)
        .background(color = background, shape = cardShape)
        .then(
          if (danger) {
            // Chained before the border so the strip draws after the border's stroke, on top of it.
            Modifier.drawWithContent {
              drawContent()
              val stripPx = 3.dp.toPx()
              drawRect(color = strip, topLeft = Offset(0f, 0f), size = Size(stripPx, size.height))
            }
          } else {
            Modifier
          }
        )
        .border(width = 1.5.dp, color = borderColor, shape = cardShape)
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
