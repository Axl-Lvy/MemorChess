package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** Tabs shown in the mobile info section. */
enum class MobileInfoTab(val title: String) {
  LINES("Lines"),
  LICHESS("Lichess"),
  STATS("Stats"),
}

/**
 * Mobile lines / lichess / stats info tabs rendered beneath the board.
 *
 * @param nextMoves List of SAN continuations rendered as tap-able chips on the Lines tab.
 * @param onPlayMove Invoked when the user taps a continuation chip.
 * @param explorerViewModel View model driving the Lichess explorer tab.
 * @param onPlayLichessMove Invoked when the user taps a Lichess move.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ExploreInfoTabs(
  nextMoves: List<String>,
  onPlayMove: (String) -> Unit,
  explorerViewModel: ExplorerViewModel,
  onPlayLichessMove: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  var selected by remember { mutableStateOf(MobileInfoTab.LINES) }

  Column(
    modifier =
      modifier.background(palette.panel).border(width = 1.dp, color = palette.line).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    KineticSegmentedControl(
      options = MobileInfoTab.entries.toList(),
      selected = selected,
      onSelect = { selected = it },
      modifier = Modifier.fillMaxWidth(),
      label = { it.title },
    )

    when (selected) {
      MobileInfoTab.LINES -> {
        if (nextMoves.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "No continuations stored.",
              style = typography.monoSm.copy(color = palette.ink3),
            )
          }
        } else {
          FlowRow(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            nextMoves.forEach { san ->
              Box(
                modifier =
                  Modifier.background(palette.panel2)
                    .border(width = 1.dp, color = palette.line)
                    .clickable { onPlayMove(san) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(text = san, style = typography.mono.copy(color = palette.ink))
              }
            }
          }
        }
      }
      MobileInfoTab.LICHESS ->
        LichessExplorerPanel(
          viewModel = explorerViewModel,
          onClickMove = onPlayLichessMove,
          modifier = Modifier.fillMaxWidth(),
        )
      MobileInfoTab.STATS ->
        Column(
          modifier =
            Modifier.fillMaxWidth().heightIn(min = 80.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "Stats coming soon",
            style = typography.monoSm.copy(color = palette.ink3),
            modifier = Modifier.padding(8.dp),
          )
        }
    }
  }
}
