package proj.ankichess.axl.core.game.pieces.vectors

import proj.ankichess.axl.core.game.Game
import proj.ankichess.axl.core.game.pieces.IPiece

/** King. */
class King(player: Game.Player) : AFiniteMovers(player) {
  override fun getVectors(): Set<Pair<Int, Int>> {
    return VectorUtils.ALL_VECTORS
  }

  override fun baseChar(): String {
    return IPiece.KING
  }
}
