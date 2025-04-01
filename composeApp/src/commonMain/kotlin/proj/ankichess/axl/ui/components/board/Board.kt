package proj.ankichess.axl.ui.components.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import proj.ankichess.axl.core.impl.interactions.InteractionManager
import proj.ankichess.axl.core.intf.engine.board.ITile
import proj.ankichess.axl.core.intf.engine.pieces.IPiece
import proj.ankichess.axl.ui.util.intf.IReloader

@Composable
fun Board(
  inverted: Boolean = false,
  interactionManager: InteractionManager,
  reloader: IReloader,
  modifier: Modifier = Modifier,
) {
  val board = interactionManager.game.position.board

  fun squareIndexToBoardTile(index: Int): Pair<Int, Int> {
    return if (inverted) {
      Pair(index / 8, (63 - index) % 8)
    } else {
      Pair((63 - index) / 8, index % 8)
    }
  }
  val tileToPiece =
    remember(reloader.getKey()) {
      mutableStateMapOf<ITile, IPiece?>().apply {
        board.getTilesIterator().forEach { put(it, it.getSafePiece()) }
      }
    }
  LazyVerticalGrid(columns = GridCells.Fixed(8), modifier = modifier) {
    items(64) { index ->
      val coords = squareIndexToBoardTile(index)
      run {
        Box(
          modifier =
            Modifier.clickable(
                onClick = {
                  interactionManager.clickOnTile(coords)
                  for (boardTile in board.getTilesIterator()) {
                    if (tileToPiece[boardTile] != boardTile.getSafePiece()) {
                      tileToPiece[boardTile] = boardTile.getSafePiece()
                    }
                  }
                }
              )
              .aspectRatio(1f)
        ) {
          val tile = board.getTile(coords)
          Tile(tile)
          tileToPiece[tile]?.let { Piece(it) }
        }
      }
    }
  }
}
