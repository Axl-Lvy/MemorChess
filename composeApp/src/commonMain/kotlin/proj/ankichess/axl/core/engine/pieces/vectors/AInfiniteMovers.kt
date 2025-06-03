package proj.ankichess.axl.core.engine.pieces.vectors

import proj.ankichess.axl.core.engine.Game
import proj.ankichess.axl.core.engine.moves.description.MoveDescription

/** Piece that can move infinitely by adding multiple times [its vectors][getVectors]. */
abstract class AInfiniteMovers(player: Game.Player) : AVectorizedMovers(player) {
  override fun availableMoves(coords: Pair<Int, Int>): List<List<MoveDescription>> {
    val vectors = getVectors()
    val moves = mutableListOf<List<MoveDescription>>()
    for (vector in vectors) {
      val moveLine = mutableListOf<MoveDescription>()
      var result = VectorUtils.addVector(coords, vector)
      while (result != null) {
        moveLine.add(MoveDescription(coords, result))
        result = VectorUtils.addVector(result, vector)
      }
      if (moveLine.isNotEmpty()) {
        moves.add(moveLine)
      }
    }
    return moves
  }

  override fun isMovePossible(move: MoveDescription): Boolean {
    return getVectors().contains(move.getSubVector())
  }
}
