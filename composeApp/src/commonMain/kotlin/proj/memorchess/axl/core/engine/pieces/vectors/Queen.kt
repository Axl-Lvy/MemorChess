package proj.memorchess.axl.core.engine.pieces.vectors

import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.Piece

/** Queen. */
class Queen(player: Game.Player) : InfiniteMovers(player) {
  override fun getVectors(): Set<Pair<Int, Int>> {
    return VectorUtils.ALL_VECTORS
  }

  override fun baseChar(): String {
    return Piece.QUEEN
  }

  companion object {
    fun white() = Queen(Game.Player.WHITE)

    fun black() = Queen(Game.Player.BLACK)
  }
}
