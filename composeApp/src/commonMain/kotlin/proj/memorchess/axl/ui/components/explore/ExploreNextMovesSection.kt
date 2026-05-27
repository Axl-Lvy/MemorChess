package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * Legacy next-moves grid used by call sites that still consume the `nextMoveButtons: @Composable ()
 * -> List<@Composable () -> Unit>` slot directly. Each move is rendered inside a Kinetic card; the
 * supplied composable handles the click target.
 */
@Composable
fun ExploreNextMovesSection(
  modifier: Modifier = Modifier,
  nextMoveButtons: @Composable () -> List<@Composable () -> Unit>,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val moves = nextMoveButtons()
  if (moves.isEmpty()) {
    Box(
      modifier =
        modifier
          .fillMaxWidth()
          .background(palette.panel2)
          .border(width = 1.dp, color = palette.line)
          .heightIn(min = 48.dp)
          .padding(12.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(text = "No next moves", style = typography.monoSm.copy(color = palette.ink3))
    }
    return
  }
  LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(max = 360.dp)
        .background(palette.panel)
        .border(width = 1.dp, color = palette.line)
        .padding(8.dp)
        .testTag("NextMovesBar"),
    verticalArrangement = Arrangement.spacedBy(6.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    itemsIndexed(moves) { _, moveButton ->
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .background(palette.panel2)
            .border(width = 1.dp, color = palette.line)
            .heightIn(min = 44.dp),
        contentAlignment = Alignment.Center,
      ) {
        moveButton()
      }
    }
  }
}
