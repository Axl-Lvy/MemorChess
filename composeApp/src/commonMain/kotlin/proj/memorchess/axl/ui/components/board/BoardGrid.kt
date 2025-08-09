package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.description_board_tile
import org.jetbrains.compose.resources.stringResource

@Composable
fun BoardGrid(state: BoardGridState, modifier: Modifier = Modifier) {
  val scope = rememberCoroutineScope()

  Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
    Column {
      for (rowIndex in 0..7) {
        Row(modifier = Modifier.fillMaxSize().weight(1f)) {
          for (colIndex in 0..7) {
            val index = rowIndex * 8 + colIndex
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
                  .weight(1f)
            ) {
              Tile(tile)
              state.tileToPiece[tile.gridItem]?.let { Piece(it, Modifier.fillMaxSize()) }
            }
          }
        }
      }
    }
  }
}
