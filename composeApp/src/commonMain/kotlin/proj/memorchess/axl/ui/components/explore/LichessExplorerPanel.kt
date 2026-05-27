package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.core.data.explorer.ExplorerSource
import proj.memorchess.axl.core.data.explorer.ExplorerState
import proj.memorchess.axl.core.data.explorer.ExplorerViewModel
import proj.memorchess.axl.core.data.explorer.LichessExplorerMove
import proj.memorchess.axl.core.data.explorer.LichessExplorerResponse
import proj.memorchess.axl.ui.components.controls.KineticSegmentedControl
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

private const val TEST_TAG_ROOT = "lichess_explorer_panel"
private const val TEST_TAG_MOVE_ROW = "lichess_explorer_move_row"

/**
 * Read only side panel that displays popular moves from the Lichess Opening Explorer for the
 * position currently shown in the explore view.
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
  LichessExplorerPanelContent(
    state = state,
    source = source,
    onSetSource = viewModel::setSource,
    onClickMove = onClickMove,
    modifier = modifier,
  )
}

/**
 * Stateless rendering of the explorer panel. Split out from [LichessExplorerPanel] so tests can
 * drive each [ExplorerState] without standing up a full [ExplorerViewModel].
 */
@Composable
internal fun LichessExplorerPanelContent(
  state: ExplorerState,
  source: ExplorerSource,
  onSetSource: (ExplorerSource) -> Unit,
  onClickMove: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalKineticPalette.current
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .background(palette.panel)
        .border(width = 1.dp, color = palette.line)
        .padding(12.dp)
        .testTag(TEST_TAG_ROOT),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    OpeningHeader(state)
    SourceToggle(selected = source, onSelect = onSetSource)
    Body(state, onClickMove = onClickMove)
  }
}

@Composable
private fun OpeningHeader(state: ExplorerState) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val opening =
    when (state) {
      is ExplorerState.Loaded -> state.response.opening
      else -> null
    }
  if (opening == null) {
    Text(text = "LICHESS EXPLORER", style = typography.monoSm.copy(color = palette.accentText))
  } else {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(text = opening.name, style = typography.display.copy(color = palette.ink))
      Text(text = "ECO ${opening.eco}", style = typography.monoSm.copy(color = palette.ink3))
    }
  }
}

@Composable
private fun SourceToggle(selected: ExplorerSource, onSelect: (ExplorerSource) -> Unit) {
  KineticSegmentedControl(
    options = listOf(ExplorerSource.MASTERS, ExplorerSource.LICHESS),
    selected = selected,
    onSelect = onSelect,
    modifier = Modifier.fillMaxWidth(),
    label = {
      when (it) {
        ExplorerSource.MASTERS -> "Masters"
        ExplorerSource.LICHESS -> "Lichess"
      }
    },
  )
}

@Composable
private fun Body(state: ExplorerState, onClickMove: (String) -> Unit) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  when (state) {
    is ExplorerState.Idle ->
      Text(
        "Make a move to load popular replies.",
        style = typography.bodySm.copy(color = palette.ink3),
      )
    is ExplorerState.Loading ->
      Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    is ExplorerState.Loaded -> LoadedBody(response = state.response, onClickMove = onClickMove)
    is ExplorerState.RateLimited ->
      Text(
        "Lichess rate limited the request. Try again in a moment.",
        style = typography.bodySm.copy(color = palette.red),
      )
    is ExplorerState.Unauthorized ->
      Text(
        "Sign in to Lichess from Settings to use the opening explorer.",
        style = typography.bodySm.copy(color = palette.red),
      )
    is ExplorerState.Error ->
      Text(
        "Could not load explorer: ${state.message}",
        style = typography.bodySm.copy(color = palette.red),
      )
  }
}

@Composable
private fun LoadedBody(response: LichessExplorerResponse, onClickMove: (String) -> Unit) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  if (response.moves.isEmpty()) {
    Text("No games found for this position.", style = typography.bodySm.copy(color = palette.ink3))
    return
  }
  LazyColumn(
    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    items(response.moves, key = { it.uci }) { move ->
      MoveRow(move = move, onClick = { onClickMove(move.san) })
    }
  }
}

@Composable
private fun MoveRow(move: LichessExplorerMove, onClick: () -> Unit) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .testTag("$TEST_TAG_MOVE_ROW:${move.san}")
        .background(palette.panel2)
        .border(width = 1.dp, color = palette.line)
        .clickable { onClick() }
        .padding(vertical = 6.dp, horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = move.san,
      style = typography.mono.copy(color = palette.ink),
      modifier = Modifier.width(60.dp),
    )
    Text(
      text = move.totalGames.toReadableCount(),
      style = typography.monoSm.copy(color = palette.ink3),
      modifier = Modifier.width(56.dp),
    )
    StackedResultBar(move = move, modifier = Modifier.weight(1f).height(14.dp))
  }
}

@Composable
private fun StackedResultBar(move: LichessExplorerMove, modifier: Modifier = Modifier) {
  val palette = LocalKineticPalette.current
  val total = move.totalGames.coerceAtLeast(1L).toFloat()
  val whiteWeight = (move.white / total).coerceAtLeast(MIN_WEIGHT)
  val drawWeight = (move.draws / total).coerceAtLeast(MIN_WEIGHT)
  val blackWeight = (move.black / total).coerceAtLeast(MIN_WEIGHT)
  Row(modifier = modifier.background(palette.panel3)) {
    if (move.white > 0) {
      Box(modifier = Modifier.weight(whiteWeight).fillMaxHeight().background(palette.green))
    }
    if (move.draws > 0) {
      Box(modifier = Modifier.weight(drawWeight).fillMaxHeight().background(palette.ink3))
    }
    if (move.black > 0) {
      Box(modifier = Modifier.weight(blackWeight).fillMaxHeight().background(palette.red))
    }
  }
}

private const val MIN_WEIGHT = 0.0001f

private fun Long.toReadableCount(): String =
  when {
    this >= 1_000_000 -> "${this / 1_000_000}M"
    this >= 1_000 -> "${this / 1_000}k"
    else -> toString()
  }
