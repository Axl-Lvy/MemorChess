package proj.ankichess.axl.core.engine.pieces.vectors

import proj.ankichess.axl.core.engine.Game
import proj.ankichess.axl.core.engine.pieces.IPiece

/** Bishop. */
class Bishop(player: Game.Player) : AInfiniteMovers(player) {
  override fun getVectors(): Set<Pair<Int, Int>> {
    return VectorUtils.DIAG_VECTORS
  }

  override fun baseChar(): String {
    return IPiece.BISHOP
  }
}
