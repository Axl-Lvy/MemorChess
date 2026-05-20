package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.core.data.explorer.ExplorerSource
import proj.memorchess.axl.core.data.explorer.ExplorerState
import proj.memorchess.axl.core.data.explorer.ExplorerViewModel
import proj.memorchess.axl.core.data.explorer.LichessExplorerMove
import proj.memorchess.axl.core.data.explorer.LichessExplorerResponse

private const val TEST_TAG_ROOT = "lichess_explorer_panel"
private const val TEST_TAG_MOVE_ROW = "lichess_explorer_move_row"

/**
 * Read only side panel that displays popular moves from the Lichess Opening Explorer for the
 * position currently shown in the explore view.
 *
 * The panel does not own the chessboard; it only renders the explorer response and surfaces a
 * source toggle (masters vs lichess). Clicking a move row delegates to [onClickMove] which the
 * caller wires to the existing playMove flow so that the same code path handles in tree moves and
 * Lichess sourced moves.
 *
 * @param viewModel Drives fetches and exposes the state to render.
 * @param onClickMove Invoked when the user taps a move row, with the SAN as argument.
 */
@Composable
fun LichessExplorerPanel(
  viewModel: ExplorerViewModel,
  onClickMove: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsState()
  val source by viewModel.source.collectAsState()

  Surface(
    modifier = modifier.fillMaxWidth().testTag(TEST_TAG_ROOT),
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      OpeningHeader(state)
      SourceToggle(selected = source, onSelect = viewModel::setSource)
      Body(state, onClickMove = onClickMove)
    }
  }
}

@Composable
private fun OpeningHeader(state: ExplorerState) {
  val opening =
    when (state) {
      is ExplorerState.Loaded -> state.response.opening
      else -> null
    }
  if (opening == null) {
    Text(
      text = "Lichess explorer",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
    )
  } else {
    Column {
      Text(
        text = opening.name,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = "ECO ${opening.eco}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SourceToggle(selected: ExplorerSource, onSelect: (ExplorerSource) -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FilterChip(
      selected = selected == ExplorerSource.MASTERS,
      onClick = { onSelect(ExplorerSource.MASTERS) },
      label = { Text("Masters") },
    )
    FilterChip(
      selected = selected == ExplorerSource.LICHESS,
      onClick = { onSelect(ExplorerSource.LICHESS) },
      label = { Text("Lichess") },
    )
  }
}

@Composable
private fun Body(state: ExplorerState, onClickMove: (String) -> Unit) {
  when (state) {
    is ExplorerState.Idle ->
      Text("Make a move to load popular replies.", style = MaterialTheme.typography.bodySmall)
    is ExplorerState.Loading ->
      Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    is ExplorerState.Loaded -> LoadedBody(response = state.response, onClickMove = onClickMove)
    is ExplorerState.RateLimited ->
      Text(
        "Lichess rate limited the request. Try again in a moment.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    is ExplorerState.Error ->
      Text(
        "Could not load explorer: ${state.message}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
  }
}

@Composable
private fun LoadedBody(response: LichessExplorerResponse, onClickMove: (String) -> Unit) {
  if (response.moves.isEmpty()) {
    Text(
      "No games found for this position.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }
  LazyColumn(
    modifier = Modifier.fillMaxWidth().height(200.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    items(response.moves, key = { it.uci }) { move ->
      MoveRow(move = move, onClick = { onClickMove(move.san) })
    }
  }
}

@Composable
private fun MoveRow(move: LichessExplorerMove, onClick: () -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .testTag("$TEST_TAG_MOVE_ROW:${move.san}")
        .clickable { onClick() }
        .padding(vertical = 4.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = move.san,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.padding(end = 8.dp),
    )
    Text(
      text = move.totalGames.toReadableCount(),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(end = 8.dp),
    )
    StackedResultBar(move = move, modifier = Modifier.weight(1f).height(12.dp))
  }
}

@Composable
private fun StackedResultBar(move: LichessExplorerMove, modifier: Modifier = Modifier) {
  val total = move.totalGames.coerceAtLeast(1L).toFloat()
  Row(modifier = modifier) {
    Box(
      modifier =
        Modifier.weight(move.white / total)
          .height(12.dp)
          .background(Color(0xFFE8E8E8), RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
    )
    Box(modifier = Modifier.weight(move.draws / total).height(12.dp).background(Color(0xFFB0B0B0)))
    Box(
      modifier =
        Modifier.weight(move.black / total)
          .height(12.dp)
          .background(Color(0xFF222222), RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
    )
  }
}

private fun Long.toReadableCount(): String =
  when {
    this >= 1_000_000 -> "${this / 1_000_000}M"
    this >= 1_000 -> "${this / 1_000}k"
    else -> toString()
  }
