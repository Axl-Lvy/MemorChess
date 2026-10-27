package proj.memorchess.axl.ui.components.board

import androidx.compose.runtime.mutableStateMapOf
import proj.memorchess.axl.core.engine.BoardLocation
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.core.interactions.InteractionsManager

/** State of the chess board grid for rendering and interactions. */
class BoardGridState(val inverted: Boolean, val interactionsManager: InteractionsManager) {
  /** Maps each board location to its current piece, or null if empty. */
  val tileToPiece = mutableStateMapOf<BoardLocation, ChessPiece?>()
  /** Maps source locations to their destination locations for pieces to move. */
  val piecesToMove = mutableStateMapOf<BoardLocation, BoardLocation>()

  /** Returns the currently selected tile coordinates, or null if none is selected. */
  val selectedTile: Pair<Int, Int>?
    get() = interactionsManager.selectedTile

  init {
    for (row in 0..7) {
      for (col in 0..7) {
        tileToPiece[BoardLocation(row, col)] = interactionsManager.engine.pieceAt(row, col)
      }
    }
    interactionsManager.registerCallBack {
      if (it) {
        updateTilesWithAnimation()
      } else {
        updateTilesWithoutAnimation()
      }
    }
  }

  /** Forwards a click on the tile at [coords] to the [interactionsManager]. */
  suspend fun onTileClick(coords: Pair<Int, Int>) {
    interactionsManager.clickOnTile(coords)
  }

  /** Completes the pending promotion with [pieceKind]. */
  suspend fun applyPromotion(pieceKind: PieceKind) {
    interactionsManager.applyPromotion(pieceKind)
  }

  private fun updateTilesWithoutAnimation() {
    for (row in 0..7) {
      for (col in 0..7) {
        val loc = BoardLocation(row, col)
        val newPiece = interactionsManager.engine.pieceAt(row, col)
        if (tileToPiece[loc] != newPiece) {
          tileToPiece[loc] = newPiece
        }
      }
    }
  }

  /** Syncs tiles with the engine and records the moved pieces in [piecesToMove]. */
  private fun updateTilesWithAnimation() {
    piecesToMove.clear()
    val previousPiecePositions = mutableMapOf<ChessPiece, BoardLocation>()
    val newPiecePositions = mutableMapOf<ChessPiece, BoardLocation>()
    for (row in 0..7) {
      for (col in 0..7) {
        val loc = BoardLocation(row, col)
        val newPiece = interactionsManager.engine.pieceAt(row, col)
        val previousPiece = tileToPiece[loc]
        if (previousPiece != newPiece) {
          if (newPiece != null) {
            newPiecePositions[newPiece] = loc
          } else if (previousPiece != null) {
            previousPiecePositions[previousPiece] = loc
          }
          tileToPiece[loc] = newPiece
        }
      }
    }
    previousPiecePositions.forEach { (piece, previousPosition) ->
      val newPosition = newPiecePositions[piece]
      if (newPosition != null) {
        piecesToMove[previousPosition] = newPosition
      }
    }
  }

  /** Returns the board location at grid [index]. */
  fun getBoardLocationAt(index: Int): BoardLocation {
    val coords = squareIndexToBoardTile(index)
    return BoardLocation(coords.first, coords.second)
  }

  /** Converts grid [index] to board coordinates, honoring [inverted]. */
  fun squareIndexToBoardTile(index: Int): Pair<Int, Int> {
    return if (inverted) {
      Pair(index / 8, (63 - index) % 8)
    } else {
      Pair((63 - index) / 8, index % 8)
    }
  }
}
