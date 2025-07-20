package proj.memorchess.axl.core.engine.pieces.vectors

import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.PieceImpl

/** A vectorized mover is a piece that moves using a vector added to its position. */
abstract class VectorizedMovers(player: Game.Player) : PieceImpl(player) {

  /**
   * Gets the vectors associated with this piece. [Available moves][availableMoves] will be computed
   * by adding these vectors to the piece position.
   *
   * @return
   */
  abstract fun getVectors(): Set<Pair<Int, Int>>
}
