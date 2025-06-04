package proj.ankichess.axl.core.engine.pieces.vectors

import proj.ankichess.axl.core.engine.Game
import proj.ankichess.axl.core.engine.pieces.IPiece

/** Rook. */
class Rook(player: Game.Player) :
  proj.ankichess.axl.core.engine.pieces.vectors.AInfiniteMovers(player) {
  override fun getVectors(): Set<Pair<Int, Int>> {
    return proj.ankichess.axl.core.engine.pieces.vectors.VectorUtils.STRAIGHT_VECTORS
  }

  override fun baseChar(): String {
    return IPiece.ROOK
  }
}
