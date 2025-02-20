package proj.ankichess.axl.core.game.pieces.vectors

import proj.ankichess.axl.core.game.Game
import proj.ankichess.axl.core.game.pieces.IPiece

/** Bishop. */
class Bishop(player: Game.Player) : AInfiniteMovers(player) {
  override fun getVectors(): Set<Pair<Int, Int>> {
    return DIAG_VECTORS
  }

  override fun baseChar(): String {
    return IPiece.BISHOP
  }
}
