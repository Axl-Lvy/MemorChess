package proj.ankichess.axl.board

import proj.ankichess.axl.core.game.Game
import proj.ankichess.axl.core.game.moves.description.MoveDescription

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

  /**
   * Clicks on a tile.
   *
   * @param coordinates The clicked tile's coordinates.
   */
  fun clickOnTile(coordinates: Pair<Int, Int>) {
    val immutableFirstTile = firstTile
    if (immutableFirstTile != null) {
      game.safePlayMove(MoveDescription(immutableFirstTile, coordinates))
      firstTile = null
    } else if (game.board.getTile(coordinates).getSafePiece() != null) {
      firstTile = coordinates
    }
  }
}
