package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.description_board_tile
import org.jetbrains.compose.resources.stringResource

@Composable
fun BoardGrid(state: BoardGridState, modifier: Modifier = Modifier) {
  val scope = rememberCoroutineScope()

  LazyVerticalGrid(columns = GridCells.Fixed(8), modifier = modifier) {
    items(64) { index ->
      val tile = state.getTileAt(index)
      val coords = tile.getCoords()
      val clickLabel = stringResource(Res.string.description_board_tile, tile.getName())

      Box(
        modifier =
          Modifier.clickable(
              onClickLabel = clickLabel,
              onClick = { scope.launch { state.onTileClick(coords) } },
            )
            .aspectRatio(1f)
      ) {
        Tile(tile)
        state.tileToPiece[tile.gridItem]?.let { Piece(it, Modifier.fillMaxSize()) }
      }
    }
  }
}
