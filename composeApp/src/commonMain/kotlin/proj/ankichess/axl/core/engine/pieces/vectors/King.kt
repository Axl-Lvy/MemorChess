package proj.ankichess.axl.core.engine.pieces.vectors

import proj.ankichess.axl.core.engine.Game
import proj.ankichess.axl.core.engine.pieces.IPiece

/** King. */
class King(player: Game.Player) : AFiniteMovers(player) {
  override fun getVectors(): Set<Pair<Int, Int>> {
    return VectorUtils.ALL_VECTORS
  }

  override fun baseChar(): String {
    return IPiece.KING
  }
}
