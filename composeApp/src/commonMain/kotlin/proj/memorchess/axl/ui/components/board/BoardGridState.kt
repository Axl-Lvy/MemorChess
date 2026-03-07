package proj.memorchess.axl.ui.components.board

import androidx.compose.runtime.mutableStateMapOf
import proj.memorchess.axl.core.engine.BoardLocation
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.core.interactions.InteractionsManager

/**
 * Holds and manages the state of the chess board grid for UI rendering and interactions.
 *
 * @property inverted Whether the board is displayed inverted.
 * @property interactionsManager The manager handling board interactions and game state.
 */
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

  /**
   * Handles a tile click event and delegates to the interactions manager.
   *
   * @param coords The coordinates of the clicked tile.
   */
  suspend fun onTileClick(coords: Pair<Int, Int>) {
    interactionsManager.clickOnTile(coords)
  }

  /**
   * Applies a promotion for the given piece kind via the interactions manager.
   *
   * @param pieceKind The piece kind to promote to.
   */
  suspend fun applyPromotion(pieceKind: PieceKind) {
    interactionsManager.applyPromotion(pieceKind)
  }

  /** Updates the board tiles without animation, syncing state with the game engine. */
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

  /** Updates the board tiles with animation, calculating piece movement between positions. */
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

  /**
   * Retrieves the board location at the specified grid index.
   *
   * @param index The grid index.
   * @return The board location at the given index.
   */
  fun getBoardLocationAt(index: Int): BoardLocation {
    val coords = squareIndexToBoardTile(index)
    return BoardLocation(coords.first, coords.second)
  }

  /**
   * Converts a square index to board tile coordinates.
   *
   * @param index The index to convert.
   * @return The corresponding board tile coordinates.
   */
  fun squareIndexToBoardTile(index: Int): Pair<Int, Int> {
    return if (inverted) {
      Pair(index / 8, (63 - index) % 8)
    } else {
      Pair((63 - index) / 8, index % 8)
    }
  }
}
