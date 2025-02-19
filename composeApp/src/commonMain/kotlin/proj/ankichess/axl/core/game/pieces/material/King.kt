package proj.ankichess.axl.core.game.pieces.material

import proj.ankichess.axl.core.game.Game

/** King. */
class King(player: Game.Player) : AFiniteMovers(player) {
  override fun getVectors(): Set<Pair<Int, Int>> {
    return ALL_VECTORS
  }

  override fun baseChar(): String {
    return IPiece.KING
  }
}
