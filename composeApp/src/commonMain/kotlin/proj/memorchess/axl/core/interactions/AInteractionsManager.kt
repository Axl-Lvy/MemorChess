package proj.memorchess.axl.core.interactions

import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.moves.IllegalMoveException
import proj.memorchess.axl.core.engine.moves.description.MoveDescription
import proj.memorchess.axl.core.util.IReloader
import proj.memorchess.axl.ui.components.popup.info

/**
 * Class that handles clicks on the chess board.
 *
 * @param game The game that is being played.
 * @constructor Creates an interaction manager from a game.
 */
abstract class AInteractionsManager(var game: Game) {

  /** Coordinates of the tile that was clicked first. */
  private var firstTile: Pair<Int, Int>? = null

  /**
   * Clicks on a tile.
   *
   * @param coordinates The clicked tile's coordinates.
   * @param reloader The reloader to use after the move is played.
   */
  suspend fun clickOnTile(coordinates: Pair<Int, Int>, reloader: IReloader) {
    val immutableFirstTile = firstTile
    if (immutableFirstTile != null) {
      try {
        val move = game.playMove(MoveDescription(immutableFirstTile, coordinates))
        afterPlayMove(move, reloader)
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
  suspend fun playMove(move: String, reloader: IReloader) {
    game.playMove(move)
    afterPlayMove(move, reloader)
  }

  /**
   * Called after a move is played.
   *
   * @param move The move that was played.
   * @param reloader The reloader to use after the move is played.
   */
  abstract suspend fun afterPlayMove(move: String, reloader: IReloader)

  /**
   * Resets the game to a new position.
   *
   * @param reloader The reloader.
   * @param position The new position key to reset the game to.
   */
  fun reset(reloader: IReloader, position: PositionKey) {
    game = Game(position)
    firstTile = null
    reloader.reload()
  }
}
