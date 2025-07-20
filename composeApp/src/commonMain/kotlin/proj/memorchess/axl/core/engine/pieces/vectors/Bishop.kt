package proj.memorchess.axl.core.engine.pieces.vectors

import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.Piece

/** Bishop. */
class Bishop(player: Game.Player) : InfiniteMovers(player) {
  override fun getVectors(): Set<Pair<Int, Int>> {
    return VectorUtils.DIAG_VECTORS
  }

  override fun baseChar(): String {
    return Piece.BISHOP
  }

  companion object {
    fun white() = Bishop(Game.Player.WHITE)

    fun black() = Bishop(Game.Player.BLACK)
  }
}
