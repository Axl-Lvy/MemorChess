package proj.memorchess.axl.core.interactions

import androidx.compose.runtime.mutableStateOf
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.moves.IllegalMoveException
import proj.memorchess.axl.core.engine.moves.description.MoveDescription
import proj.memorchess.axl.core.util.Reloader
import proj.memorchess.axl.ui.components.popup.info

/**
 * Class that handles clicks on the chess board.
 *
 * @param game The game that is being played.
 * @constructor Creates an interaction manager from a game.
 */
abstract class InteractionsManager(var game: Game) {

  /** Coordinates of the tile that was clicked first. */
  private var firstTile: Pair<Int, Int>? = null

  private var isBlocked = false

  val needPromotion = mutableStateOf(false)

  private var moveBeforePromotion: String? = null

  /**
   * Clicks on a tile.
   *
   * @param coordinates The clicked tile's coordinates.
   * @param reloader The reloader to use after the move is played.
   */
  suspend fun clickOnTile(coordinates: Pair<Int, Int>, reloader: Reloader) {
    if (isBlocked) {
      return
    }
    val immutableFirstTile = firstTile
    if (immutableFirstTile != null) {
      try {
        val move = game.playMove(MoveDescription(immutableFirstTile, coordinates))
        needPromotion.value = game.needPromotion()
        if (game.needPromotion()) {
          moveBeforePromotion = move
        } else {
          afterPlayMove(move, reloader)
        }
      } catch (e: IllegalMoveException) {
        info(e.message.toString())
      }
      firstTile = null
    } else if (
      game.position.board.getTile(coordinates).getSafePiece()?.player == game.position.playerTurn
    ) {
      firstTile = coordinates
    }
  }

  /**
   * Plays a move directly.
   *
   * @param move the move to play
   * @param reloader the reloader
   */
  suspend fun playMove(move: String, reloader: Reloader) {
    game.playMove(move)
    needPromotion.value = game.needPromotion()
    if (game.needPromotion()) {
      moveBeforePromotion = move
    } else {
      afterPlayMove(move, reloader)
    }
  }

  /**
   * Applies a promotion
   *
   * @param newPieceName The chosen piece name
   * @param reloader The reloader to use after the piece is promoted
   */
  suspend fun applyPromotion(newPieceName: String, reloader: Reloader) {
    game.applyPromotion(newPieceName)
    needPromotion.value = game.needPromotion()
    afterPlayMove(moveBeforePromotion!! + "=$newPieceName", reloader)
    moveBeforePromotion = null
  }

  /**
   * Called after a move is played.
   *
   * @param move The move that was played.
   * @param reloader The reloader to use after the move is played.
   */
  abstract suspend fun afterPlayMove(move: String, reloader: Reloader)

  /**
   * Resets the game to a new position.
   *
   * @param reloader The reloader.
   * @param position The new position key to reset the game to.
   */
  fun reset(reloader: Reloader, position: PositionIdentifier) {
    game = Game(position)
    needPromotion.value = game.needPromotion()
    firstTile = null
    reloader.reload()
  }

  /** Blocks this object. No move can be played. */
  fun block() {
    isBlocked = true
  }

  /** Unblocks this object. Moves can be played. */
  fun unblock() {
    isBlocked = false
  }
}
