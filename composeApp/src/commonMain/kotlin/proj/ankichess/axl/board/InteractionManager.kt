package proj.ankichess.axl.board

import proj.ankichess.axl.core.engine.Game
import proj.ankichess.axl.core.engine.moves.IllegalMoveException
import proj.ankichess.axl.core.engine.moves.description.MoveDescription
import proj.ankichess.axl.popup.info

/**
 * Class that handles clicks on the chess board.
 *
 * @property game The game.
 * @constructor Creates an interaction manager from a game.
 */
class InteractionManager(val game: Game) {

  /** Creates an interaction manager from a new game. */
  constructor() : this(game = Game()) {}

  /** Coordinates of the tile that was clicked first. */
  private var firstTile: Pair<Int, Int>? = null

  var error: String? = null

  /**
   * Clicks on a tile.
   *
   * @param coordinates The clicked tile's coordinates.
   */
  fun clickOnTile(coordinates: Pair<Int, Int>) {
    error = null
    val immutableFirstTile = firstTile
    if (immutableFirstTile != null) {
      try {
        game.playMove(MoveDescription(immutableFirstTile, coordinates))
      } catch (e: IllegalMoveException) {
        info(e.message.toString())
      }

      firstTile = null
    } else if (game.board.getTile(coordinates).getSafePiece() != null) {
      firstTile = coordinates
    }
  }
}
