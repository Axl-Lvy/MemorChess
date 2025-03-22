package proj.ankichess.axl.core.game.pieces.vectors

import proj.ankichess.axl.core.game.Game
import proj.ankichess.axl.core.game.pieces.APiece

/** A vectorized mover is a piece that moves using a vector added to its position. */
abstract class AVectorizedMovers(player: Game.Player) : APiece(player) {

  /**
   * Gets the vectors associated with this piece. [Available moves][availableMoves] will be computed
   * by adding these vectors to the piece position.
   *
   * @return
   */
  abstract fun getVectors(): Set<Pair<Int, Int>>
}
