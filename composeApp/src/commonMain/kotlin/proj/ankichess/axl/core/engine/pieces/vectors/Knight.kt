package proj.ankichess.axl.core.engine.pieces.vectors

import proj.ankichess.axl.core.engine.Game
import proj.ankichess.axl.core.engine.pieces.IPiece

/** Knight. */
class Knight(player: Game.Player) : AFiniteMovers(player) {
  override fun getVectors(): Set<Pair<Int, Int>> {
    return VectorUtils.KNIGHT_VECTORS
  }

  override fun baseChar(): String {
    return IPiece.KNIGHT
  }
}
