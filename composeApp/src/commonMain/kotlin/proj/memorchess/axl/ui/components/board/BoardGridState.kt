package proj.memorchess.axl.ui.components.board

import androidx.compose.runtime.mutableStateMapOf
import proj.memorchess.axl.core.engine.board.GridItem
import proj.memorchess.axl.core.engine.board.ITile
import proj.memorchess.axl.core.engine.pieces.Piece
import proj.memorchess.axl.core.interactions.InteractionsManager

class BoardGridState(val inverted: Boolean, val interactionsManager: InteractionsManager) {
  val tileToPiece = mutableStateMapOf<GridItem, Piece?>()
  val piecesToMove = mutableStateMapOf<GridItem, GridItem>()
  var board = interactionsManager.game.position.board

  /** Returns the currently selected tile coordinates, or null if none is selected. */
  val selectedTile: Pair<Int, Int>?
    get() = interactionsManager.selectedTile

  init {
    board.getTilesIterator().forEach { tile -> tileToPiece[tile.gridItem] = tile.getSafePiece() }
    interactionsManager.registerCallBack {
      if (it) {
        updateTilesWithAnimation()
      } else {
        updateTilesWithoutAnimation()
      }
    }
  }

  suspend fun onTileClick(coords: Pair<Int, Int>) {
    interactionsManager.clickOnTile(coords)
  }

  suspend fun applyPromotion(piece: Piece) {
    interactionsManager.applyPromotion(piece.toString())
  }

  private fun updateTilesWithoutAnimation() {
    board = interactionsManager.game.position.board
    for (tile in board.getTilesIterator()) {
      val newPiece = tile.getSafePiece()
      if (tileToPiece[tile.gridItem] != newPiece) {
        tileToPiece[tile.gridItem] = newPiece
      }
    }
  }

  private fun updateTilesWithAnimation() {
    piecesToMove.clear()
    board = interactionsManager.game.position.board
    val previousPiecePositions = mutableMapOf<Piece, GridItem>()
    val newPiecePositions = mutableMapOf<Piece, GridItem>()
    for (tile in board.getTilesIterator()) {
      val newPiece = tile.getSafePiece()
      val previousPiece = tileToPiece[tile.gridItem]
      if (previousPiece != newPiece) {
        if (newPiece != null) {
          newPiecePositions[newPiece] = tile.gridItem
        } else if (previousPiece != null) {
          previousPiecePositions[previousPiece] = tile.gridItem
        }
        tileToPiece[tile.gridItem] = newPiece
      }
    }
    previousPiecePositions.forEach { (piece, previousPosition) ->
      val newPosition = newPiecePositions[piece]
      if (newPosition != null) {
        piecesToMove[previousPosition] = newPosition
      }
    }
  }

  fun getTileAt(index: Int): ITile {
    val coords = squareIndexToBoardTile(index)
    return board.getTile(coords)
  }

  fun squareIndexToBoardTile(index: Int): Pair<Int, Int> {
    return if (inverted) {
      Pair(index / 8, (63 - index) % 8)
    } else {
      Pair((63 - index) / 8, index % 8)
    }
  }
}
