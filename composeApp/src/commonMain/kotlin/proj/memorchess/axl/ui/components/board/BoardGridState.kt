package proj.memorchess.axl.ui.components.board

import androidx.compose.runtime.mutableStateMapOf
import proj.memorchess.axl.core.engine.board.GridItem
import proj.memorchess.axl.core.engine.board.ITile
import proj.memorchess.axl.core.engine.pieces.Piece
import proj.memorchess.axl.core.interactions.InteractionsManager

class BoardGridState(val inverted: Boolean, val interactionsManager: InteractionsManager) {
  val tileToPiece = mutableStateMapOf<GridItem, Piece?>()
  var board = interactionsManager.game.position.board

  /** Returns the currently selected tile coordinates, or null if none is selected. */
  val selectedTile: Pair<Int, Int>?
    get() = interactionsManager.selectedTile

  init {
    board.getTilesIterator().forEach { tile -> tileToPiece[tile.gridItem] = tile.getSafePiece() }
    interactionsManager.registerCallBack { updateTiles() }
  }

  suspend fun onTileClick(coords: Pair<Int, Int>) {
    interactionsManager.clickOnTile(coords)
  }

  suspend fun applyPromotion(piece: Piece) {
    interactionsManager.applyPromotion(piece.toString())
  }

  private fun updateTiles() {
    board = interactionsManager.game.position.board
    for (tile in board.getTilesIterator()) {
      val newPiece = tile.getSafePiece()
      if (tileToPiece[tile.gridItem] != newPiece) {
        tileToPiece[tile.gridItem] = newPiece
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
