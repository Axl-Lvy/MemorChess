package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.core.data.explorer.ExplorerViewModel
import proj.memorchess.axl.ui.components.controls.KineticSegmentedControl
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/** Tabs shown in the desktop sidebar. */
enum class SidebarTab(val title: String) {
  CONTINUATIONS("Continuations"),
  LICHESS("Lichess"),
  NOTES("Notes"),
}

/**
 * Right rail used in the landscape explore layout.
 *
 * @param nextMoves List of SAN continuations clickable in the grid.
 * @param onPlayMove Invoked when the user taps a continuation.
 * @param explorerViewModel View model driving the Lichess opening explorer tab.
 * @param onPlayLichessMove Invoked when the user taps a Lichess explorer move.
 */
@Composable
fun ExploreSidebar(
  nextMoves: List<String>,
  onPlayMove: (String) -> Unit,
  explorerViewModel: ExplorerViewModel,
  onPlayLichessMove: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  var selected by remember { mutableStateOf(SidebarTab.CONTINUATIONS) }

  Column(
    modifier =
      modifier.background(palette.panel).border(width = 1.dp, color = palette.line).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    KineticSegmentedControl(
      options = SidebarTab.entries.toList(),
      selected = selected,
      onSelect = { selected = it },
      modifier = Modifier.fillMaxWidth(),
      label = { it.title },
    )

    when (selected) {
      SidebarTab.CONTINUATIONS -> ContinuationGrid(nextMoves = nextMoves, onPlay = onPlayMove)
      SidebarTab.LICHESS ->
        LichessExplorerPanel(
          viewModel = explorerViewModel,
          onClickMove = onPlayLichessMove,
          modifier = Modifier.fillMaxWidth(),
        )
      SidebarTab.NOTES ->
        Box(
          modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(text = "Notes coming soon", style = typography.monoSm.copy(color = palette.ink3))
        }
    }
  }
}

/** 2-column grid of continuation move cards. Each card shows the SAN in mono. */
@Composable
private fun ContinuationGrid(nextMoves: List<String>, onPlay: (String) -> Unit) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current

  if (nextMoves.isEmpty()) {
    Box(
      modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = "No continuations stored at this position.",
        style = typography.monoSm.copy(color = palette.ink3),
      )
    }
    return
  }

  LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(nextMoves) { san ->
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .background(palette.panel2)
            .border(width = 1.dp, color = palette.line)
            .clickable { onPlay(san) }
            .padding(vertical = 12.dp, horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
      ) {
        Text(text = san, style = typography.mono.copy(color = palette.ink))
      }
    }
  }
}
