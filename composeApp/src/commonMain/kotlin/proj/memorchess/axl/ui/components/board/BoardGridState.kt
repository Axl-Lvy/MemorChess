package proj.memorchess.axl.ui.components.board

import androidx.compose.runtime.mutableStateMapOf
import proj.memorchess.axl.core.engine.board.BoardLocation
import proj.memorchess.axl.core.engine.board.ITile
import proj.memorchess.axl.core.engine.pieces.Piece
import proj.memorchess.axl.core.interactions.InteractionsManager

/**
 * Holds and manages the state of the chess board grid for UI rendering and interactions.
 *
 * @property inverted Whether the board is displayed inverted.
 * @property interactionsManager The manager handling board interactions and game state.
 */
class BoardGridState(val inverted: Boolean, val interactionsManager: InteractionsManager) {
  /** Maps each board location to its current piece, or null if empty. */
  val tileToPiece = mutableStateMapOf<BoardLocation, Piece?>()
  /** Maps source locations to their destination locations for pieces to move. */
  val piecesToMove = mutableStateMapOf<BoardLocation, BoardLocation>()
  /** The current board instance. */
  var board = interactionsManager.game.position.board

  /** Returns the currently selected tile coordinates, or null if none is selected. */
  val selectedTile: Pair<Int, Int>?
    get() = interactionsManager.selectedTile

  /** Initializes the board state and registers callbacks for board updates. */
  init {
    board.getTilesIterator().forEach { tile ->
      tileToPiece[tile.boardLocation] = tile.getSafePiece()
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
   * Applies a promotion for the given piece via the interactions manager.
   *
   * @param piece The piece to promote to.
   */
  suspend fun applyPromotion(piece: Piece) {
    interactionsManager.applyPromotion(piece.toString())
  }

  /** Updates the board tiles without animation, syncing state with the game board. */
  private fun updateTilesWithoutAnimation() {
    board = interactionsManager.game.position.board
    for (tile in board.getTilesIterator()) {
      val newPiece = tile.getSafePiece()
      if (tileToPiece[tile.boardLocation] != newPiece) {
        tileToPiece[tile.boardLocation] = newPiece
      }
    }
  }

  /** Updates the board tiles with animation, calculating piece movement between positions. */
  private fun updateTilesWithAnimation() {
    piecesToMove.clear()
    board = interactionsManager.game.position.board
    val previousPiecePositions = mutableMapOf<Piece, BoardLocation>()
    val newPiecePositions = mutableMapOf<Piece, BoardLocation>()
    for (tile in board.getTilesIterator()) {
      val newPiece = tile.getSafePiece()
      val previousPiece = tileToPiece[tile.boardLocation]
      if (previousPiece != newPiece) {
        if (newPiece != null) {
          newPiecePositions[newPiece] = tile.boardLocation
        } else if (previousPiece != null) {
          previousPiecePositions[previousPiece] = tile.boardLocation
        }
        tileToPiece[tile.boardLocation] = newPiece
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
   * Retrieves the tile at the specified index.
   *
   * @param index The index of the tile.
   * @return The tile at the given index.
   */
  fun getTileAt(index: Int): ITile {
    val coords = squareIndexToBoardTile(index)
    return board.getTile(coords)
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
