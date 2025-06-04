package proj.memorchess.axl.core.engine.pieces.vectors

import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.moves.Castle
import proj.memorchess.axl.core.engine.moves.description.MoveDescription
import proj.memorchess.axl.core.engine.pieces.IPiece

/** King. */
class King(player: Game.Player) :
  proj.memorchess.axl.core.engine.pieces.vectors.AFiniteMovers(player) {
  override fun getVectors(): Set<Pair<Int, Int>> {
    return proj.memorchess.axl.core.engine.pieces.vectors.VectorUtils.ALL_VECTORS
  }

  override fun baseChar(): String {
    return IPiece.KING
  }

  override fun isMovePossible(move: MoveDescription): Boolean {
    Castle.castles.forEach { castle ->
      if (move.from == castle.origin() && move.to == castle.destination()) {
        return true
      }
    }
    return super.isMovePossible(move)
  }
}
