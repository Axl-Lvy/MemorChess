package proj.ankichess.axl.core.game.pieces.material

import proj.ankichess.axl.core.game.Game

/** Queen. */
class Queen(player: Game.Player) : AInfiniteMovers(player) {
  override fun getVectors(): Set<Pair<Int, Int>> {
    return ALL_VECTORS
  }

  override fun baseChar(): String {
    return IPiece.QUEEN
  }
}
