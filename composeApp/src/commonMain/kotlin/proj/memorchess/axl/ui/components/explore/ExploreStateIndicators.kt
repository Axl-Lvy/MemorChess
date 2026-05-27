package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.LocalKineticPalette

/**
 * Section hosting the node-state indicator. Restyled as a Kinetic pill: `panel` background, 1.dp
 * `line` border, content laid out inside `monoSm` uppercase by the [stateIndicators] callback.
 *
 * @param stateIndicators Composable rendering the node-state indicator.
 */
@Composable
fun ExploreStateIndicators(
  modifier: Modifier = Modifier,
  stateIndicators: @Composable (Modifier) -> Unit,
) {
  val palette = LocalKineticPalette.current
  stateIndicators(
    modifier
      .fillMaxWidth()
      .background(palette.panel)
      .border(width = 1.dp, color = palette.line)
      .padding(horizontal = 12.dp, vertical = 8.dp)
  )
}
