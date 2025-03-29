package proj.ankichess.axl.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import proj.ankichess.axl.core.engine.board.ITile
import proj.ankichess.axl.core.engine.pieces.IPiece

@Composable
fun Board(inverted: Boolean = false, reloadKey: Any, modifier: Modifier = Modifier) {
  val interactionManager = remember(reloadKey) { InteractionManager() }
  val board = interactionManager.game.position.board

  fun squareIndexToBoardTile(index: Int): Pair<Int, Int> {
    return if (inverted) {
      Pair(index / 8, (63 - index) % 8)
    } else {
      Pair((63 - index) / 8, index % 8)
    }
  }
  val tileToPiece =
    remember(reloadKey) {
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
