package proj.ankichess.axl.core.impl.engine.pieces.vectors

import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.intf.engine.pieces.IPiece

/** Queen. */
class Queen(player: Game.Player) : AInfiniteMovers(player) {
  override fun getVectors(): Set<Pair<Int, Int>> {
    return VectorUtils.ALL_VECTORS
  }

  override fun baseChar(): String {
    return IPiece.QUEEN
  }
}
