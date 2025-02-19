package proj.ankichess.axl.core.game.pieces.material

import proj.ankichess.axl.core.game.Game

/** Rook. */
class Rook(player: Game.Player) : AInfiniteMovers(player) {
  override fun getVectors(): Set<Pair<Int, Int>> {
    return STRAIGHT_VECTORS
  }

  override fun baseChar(): String {
    return IPiece.ROOK
  }
}
