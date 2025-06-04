package proj.memorchess.axl.core.engine.pieces.vectors

import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.IPiece

/** Rook. */
class Rook(player: Game.Player) :
  proj.memorchess.axl.core.engine.pieces.vectors.AInfiniteMovers(player) {
  override fun getVectors(): Set<Pair<Int, Int>> {
    return proj.memorchess.axl.core.engine.pieces.vectors.VectorUtils.STRAIGHT_VECTORS
  }

  override fun baseChar(): String {
    return IPiece.ROOK
  }
}
