package proj.memorchess.axl.core.engine.pieces.vectors

import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.Piece

/** Rook. */
class Rook(player: Game.Player) : InfiniteMovers(player) {
  override fun getVectors(): Set<Pair<Int, Int>> {
    return VectorUtils.STRAIGHT_VECTORS
  }

  override fun baseChar(): String {
    return Piece.ROOK
  }

  companion object {
    fun white() = Rook(Game.Player.WHITE)

    fun black() = Rook(Game.Player.BLACK)
  }
}
