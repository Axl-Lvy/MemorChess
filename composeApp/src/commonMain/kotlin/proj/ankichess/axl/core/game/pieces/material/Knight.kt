package proj.ankichess.axl.core.game.pieces.material

import proj.ankichess.axl.core.game.Game

/** Knight. */
class Knight(player: Game.Player) : AFiniteMovers(player) {
  override fun getVectors(): Set<Pair<Int, Int>> {
    return KNIGHT_VECTORS
  }

  override fun baseChar(): String {
    return IPiece.KNIGHT
  }
}
