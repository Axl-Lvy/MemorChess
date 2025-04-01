package proj.ankichess.axl.core.impl.engine.pieces.vectors

import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.impl.engine.moves.Castle
import proj.ankichess.axl.core.impl.engine.moves.description.MoveDescription
import proj.ankichess.axl.core.intf.engine.pieces.IPiece

/** King. */
class King(player: Game.Player) : AFiniteMovers(player) {
  override fun getVectors(): Set<Pair<Int, Int>> {
    return VectorUtils.ALL_VECTORS
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
