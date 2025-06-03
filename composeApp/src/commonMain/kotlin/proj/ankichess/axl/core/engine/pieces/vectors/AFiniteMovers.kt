package proj.ankichess.axl.core.engine.pieces.vectors

import proj.ankichess.axl.core.engine.Game
import proj.ankichess.axl.core.engine.moves.description.MoveDescription

/** A piece that can only move by adding one [vector][getVectors]. */
abstract class AFiniteMovers(player: Game.Player) : AVectorizedMovers(player) {
  override fun availableMoves(coords: Pair<Int, Int>): List<List<MoveDescription>> {
    val vectors = getVectors()
    val moves = mutableListOf<List<MoveDescription>>()
    for (vector in vectors) {
      val result = VectorUtils.addVector(coords, vector)
      if (result != null) {
        moves.add(listOf(MoveDescription(coords, result)))
      }
    }
    return moves
  }

  override fun isMovePossible(move: MoveDescription): Boolean {
    return getVectors().contains(move.getVector())
  }
}
