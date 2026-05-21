package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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

/** Tabs shown in the Explore side info panel. */
enum class ExploreInfoTab(val title: String) {
  CONTINUATIONS("Continuations"),
  LICHESS("Lichess"),
  NOTES("Notes"),
}

/**
 * Info panel rendered on the right rail (desktop / landscape) or below the board (portrait /
 * compact). The same Composable feeds both layouts — there's no behavioural or wording difference
 * between orientations, only the parent's [modifier] decides the panel's footprint.
 *
 * Three tabs:
 * - **Continuations** — saved next moves at the current position, wrapped in a [FlowRow] of mono
 *   chips so they reflow without a hard column count. Behaves well on a 280.dp landscape strip and
 *   on a 420.dp desktop column alike.
 * - **Lichess** — embedded [LichessExplorerPanel] driven by [explorerViewModel].
 * - **Notes** — placeholder for future personal annotations.
 *
 * @param nextMoves List of SAN continuations available at the current position.
 * @param onPlayMove Invoked when the user taps one of the continuation chips.
 * @param explorerViewModel View model driving the Lichess opening explorer tab.
 * @param onPlayLichessMove Invoked when the user taps a Lichess move.
 * @param modifier Outer modifier (caller sets the panel size).
 */
@OptIn(ExperimentalLayoutApi::class)
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
  var selected by remember { mutableStateOf(ExploreInfoTab.CONTINUATIONS) }

  Column(
    modifier =
      modifier.background(palette.panel).border(width = 1.dp, color = palette.line).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    KineticSegmentedControl(
      options = ExploreInfoTab.entries.toList(),
      selected = selected,
      onSelect = { selected = it },
      modifier = Modifier.fillMaxWidth(),
      label = { it.title },
    )

    when (selected) {
      ExploreInfoTab.CONTINUATIONS ->
        ContinuationsContent(nextMoves = nextMoves, onPlay = onPlayMove)
      ExploreInfoTab.LICHESS ->
        LichessExplorerPanel(
          viewModel = explorerViewModel,
          onClickMove = onPlayLichessMove,
          modifier = Modifier.fillMaxWidth(),
        )
      ExploreInfoTab.NOTES ->
        Box(
          modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(text = "Notes coming soon", style = typography.monoSm.copy(color = palette.ink3))
        }
    }
  }
}

/** Flowing row of mono SAN chips for the Continuations tab. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContinuationsContent(nextMoves: List<String>, onPlay: (String) -> Unit) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current

  if (nextMoves.isEmpty()) {
    Box(
      modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = "No continuations stored at this position.",
        style = typography.monoSm.copy(color = palette.ink3),
      )
    }
    return
  }

  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    nextMoves.forEach { san ->
      Box(
        modifier =
          Modifier.background(palette.panel2)
            .border(width = 1.dp, color = palette.line)
            .clickable { onPlay(san) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(text = san, style = typography.mono.copy(color = palette.ink))
      }
    }
  }
}
