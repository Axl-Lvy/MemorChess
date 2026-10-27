package proj.memorchess.axl.ui.components.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/** A single option in a [KineticSwatchPicker], previewed as a 2×2 checkered tile. */
data class KineticSwatch<T>(
  val value: T,
  val label: String,
  val lightSquareColor: Color,
  val darkSquareColor: Color,
)

/** Horizontally scrolling row of board-style previews, highlighting the [selected] one. */
@Composable
fun <T> KineticSwatchPicker(
  options: List<KineticSwatch<T>>,
  selected: T,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val indication = LocalIndication.current
  val scrollState = rememberScrollState()

  Row(
    modifier =
      modifier.alpha(if (enabled) 1f else 0.5f).horizontalScroll(scrollState, enabled = enabled),
    horizontalArrangement = Arrangement.spacedBy(24.dp),
    verticalAlignment = Alignment.Top,
  ) {
    options.forEach { swatch ->
      val isActive = swatch.value == selected
      val borderColor = if (isActive) palette.action else palette.line
      val borderWidth = if (isActive) 2.dp else 1.dp
      val labelColor = if (isActive) palette.ink else palette.ink3
      val interactionSource = remember(swatch.value) { MutableInteractionSource() }
      val swatchShape = RoundedCornerShape(14.dp)

      Column(
        modifier = Modifier.width(SWATCH_COLUMN_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Box(
          modifier =
            Modifier.size(48.dp)
              .clip(swatchShape)
              .border(BorderStroke(borderWidth, borderColor), swatchShape)
              .clickable(
                interactionSource = interactionSource,
                indication = indication,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = { onSelect(swatch.value) },
              )
        ) {
          Canvas(modifier = Modifier.size(48.dp)) {
            val half = size.width / 2f
            val cell = Size(half, half)
            drawRect(color = swatch.lightSquareColor, topLeft = Offset(0f, 0f), size = cell)
            drawRect(color = swatch.lightSquareColor, topLeft = Offset(half, half), size = cell)
            drawRect(color = swatch.darkSquareColor, topLeft = Offset(half, 0f), size = cell)
            drawRect(color = swatch.darkSquareColor, topLeft = Offset(0f, half), size = cell)
          }

          if (isActive) {
            Box(
              modifier =
                Modifier.align(Alignment.BottomEnd).size(16.dp).background(color = palette.action),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = palette.onAction,
                modifier = Modifier.size(12.dp),
              )
            }
          }
        }

        Text(
          text = swatch.label.uppercase(),
          style = typography.labelSm.copy(color = labelColor),
          maxLines = 2,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

/** Wider than the tile so labels like "DEFAULT LIGHT" fit, and fixed so gaps stay uniform. */
private val SWATCH_COLUMN_WIDTH = 84.dp
