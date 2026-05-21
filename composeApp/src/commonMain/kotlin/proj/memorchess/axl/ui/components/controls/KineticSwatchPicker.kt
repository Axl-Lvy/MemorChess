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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * A single option in a [KineticSwatchPicker].
 *
 * Each swatch is rendered as a 2×2 checkered tile using [lightSquareColor] in the top-left and
 * bottom-right cells and [darkSquareColor] in the top-right and bottom-left cells. The [label] is
 * shown in uppercase mono beneath the tile.
 *
 * @param T value type backing the option — typically an enum or sealed subclass identifying a board
 *   style.
 * @property value the value this swatch represents; compared by equality against the picker's
 *   `selected`.
 * @property label short uppercase-ready name of the swatch (e.g. `"classic"`).
 * @property lightSquareColor the color used for the light squares in the 2×2 preview.
 * @property darkSquareColor the color used for the dark squares in the 2×2 preview.
 */
data class KineticSwatch<T>(
  val value: T,
  val label: String,
  val lightSquareColor: Color,
  val darkSquareColor: Color,
)

/**
 * Kinetic swatch picker — a horizontal row of board-style previews. Mirrors `.swatches`, `.swatch`,
 * `.swatch.active`, and `.swatch .grid-bg` from `design-proposals/kinetic-base.css` and the Board
 * Style block in `design-proposals/kinetic-settings-desktop.html` (lines 207–243).
 *
 * Each [KineticSwatch] is drawn as a 48.dp square showing a 2×2 checkered preview of its
 * `lightSquareColor` and `darkSquareColor`, with the swatch's [KineticSwatch.label] rendered below
 * in `monoSm` uppercase. Idle swatches carry a 1.dp `line` border; the active swatch (where
 * `swatch.value == selected`) gets a 2.dp `accent` border, a small `accent` check-mark badge in the
 * bottom-right of the preview, and an `ink` label color (idle labels use `ink3`). When [enabled] is
 * false the whole row dims to 0.5 alpha and clicks are suppressed. Press/hover indication is taken
 * from [LocalIndication] so each platform's Material theme controls the ripple.
 *
 * @param T value type backing each option — typically an enum identifying a board style.
 * @param options the swatches to render, in display order.
 * @param selected the currently selected value; the swatch whose [KineticSwatch.value] equals this
 *   is highlighted.
 * @param onSelect invoked with the chosen value when the user taps a swatch.
 * @param modifier external modifier applied to the outer row.
 * @param enabled when false, clicks are disabled and the control is rendered at 0.5 alpha.
 */
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
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.Top,
  ) {
    options.forEach { swatch ->
      val isActive = swatch.value == selected
      val borderColor = if (isActive) palette.accent else palette.line
      val borderWidth = if (isActive) 2.dp else 1.dp
      val labelColor = if (isActive) palette.ink else palette.ink3
      val interactionSource = remember(swatch.value) { MutableInteractionSource() }

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Box(
          modifier =
            Modifier.size(48.dp)
              .border(BorderStroke(borderWidth, borderColor))
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
            // Top-left and bottom-right: light squares.
            drawRect(color = swatch.lightSquareColor, topLeft = Offset(0f, 0f), size = cell)
            drawRect(color = swatch.lightSquareColor, topLeft = Offset(half, half), size = cell)
            // Top-right and bottom-left: dark squares.
            drawRect(color = swatch.darkSquareColor, topLeft = Offset(half, 0f), size = cell)
            drawRect(color = swatch.darkSquareColor, topLeft = Offset(0f, half), size = cell)
          }

          if (isActive) {
            Box(
              modifier =
                Modifier.align(Alignment.BottomEnd).size(16.dp).background(color = palette.accent),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = palette.onAccent,
                modifier = Modifier.size(12.dp),
              )
            }
          }
        }

        Text(text = swatch.label.uppercase(), style = typography.monoSm.copy(color = labelColor))
      }
    }
  }
}
