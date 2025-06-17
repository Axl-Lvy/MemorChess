package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.description_board_tile
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.engine.board.ITile
import proj.memorchess.axl.core.engine.pieces.IPiece
import proj.memorchess.axl.core.interactions.AInteractionsManager
import proj.memorchess.axl.core.util.IReloader

@Composable
fun Board(
  inverted: Boolean = false,
  interactionsManager: AInteractionsManager,
  reloader: IReloader,
  modifier: Modifier = Modifier,
) {
  val board = interactionsManager.game.position.board
  val tileToPiece =
    remember(reloader.getKey()) {
      mutableStateMapOf<ITile, IPiece?>().apply {
        board.getTilesIterator().forEach { put(it, it.getSafePiece()) }
      }
    }
  val coroutineScope = rememberCoroutineScope()
  LazyVerticalGrid(columns = GridCells.Fixed(8), modifier = modifier) {
    items(64) { index ->
      val coords = squareIndexToBoardTile(index, inverted)
      val tile = board.getTile(coords)
      run {
        Box(
          modifier =
            Modifier.clickable(
                onClick = {
                  coroutineScope.launch {
                    interactionsManager.clickOnTile(coords, reloader)
                    for (boardTile in board.getTilesIterator()) {
                      if (tileToPiece[boardTile] != boardTile.getSafePiece()) {
                        tileToPiece[boardTile] = boardTile.getSafePiece()
                      }
                    }
                  }
                },
                onClickLabel = stringResource(Res.string.description_board_tile, tile.getName()),
              )
              .aspectRatio(1f)
        ) {
          Tile(tile)
          tileToPiece[tile]?.let { Piece(it) }
        }
      }
    }
  }
}

private fun squareIndexToBoardTile(index: Int, inverted: Boolean): Pair<Int, Int> {
  return if (inverted) {
    Pair(index / 8, (63 - index) % 8)
  } else {
    Pair((63 - index) / 8, index % 8)
  }
}
