package proj.memorchess.axl.core.engine.pieces.vectors

import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.IPiece

/** Knight. */
class Knight(player: Game.Player) : AFiniteMovers(player) {
  override fun getVectors(): Set<Pair<Int, Int>> {
    return VectorUtils.KNIGHT_VECTORS
  }

  override fun baseChar(): String {
    return IPiece.KNIGHT
  }

  companion object {
    fun white() = Knight(Game.Player.WHITE)

    fun black() = Knight(Game.Player.BLACK)
  }
}
