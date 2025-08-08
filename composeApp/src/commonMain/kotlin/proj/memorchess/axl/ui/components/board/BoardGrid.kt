package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.drick.compose.hotpreview.HotPreview
import de.drick.compose.hotpreview.HotPreviewParameter
import de.drick.compose.hotpreview.HotPreviewParameterProvider
import kotlinx.coroutines.launch
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.description_board_tile
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.config.CHESS_BOARD_COLOR_SETTING
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.ui.theme.ChessBoardColorScheme

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

@HotPreview(
  density = 2.625f,
  widthDp = 411,
  heightDp = 891,
  statusBar = true,
  navigationBar = de.drick.compose.hotpreview.NavigationBarMode.GestureBottom,
  displayCutout = de.drick.compose.hotpreview.DisplayCutoutMode.CameraTop,
)
@Composable
private fun BoardGridPreview(
  @HotPreviewParameter(BoardGridPreviewProvider::class) params: ChessBoardColorScheme
) {
  CHESS_BOARD_COLOR_SETTING.setValue(params)
  Column(modifier = Modifier.fillMaxSize()) {
    Text(params.displayName, modifier = Modifier.weight(1f))
    Box(modifier = Modifier.weight(8f)) {
      BoardGrid(BoardGridState(false, LinesExplorer()), Modifier.fillMaxSize())
    }
  }
}

class BoardGridPreviewProvider : HotPreviewParameterProvider<ChessBoardColorScheme> {
  override val values: Sequence<ChessBoardColorScheme>
    get() {
      return ChessBoardColorScheme.entries.asSequence()
    }
}
