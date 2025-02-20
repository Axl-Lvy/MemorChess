package proj.ankichess.axl.core.game.pieces.vectors

import proj.ankichess.axl.core.game.Game
import proj.ankichess.axl.core.game.moves.description.ClassicMoveDescription

/** A piece that can only move by adding one [vector][getVectors]. */
abstract class AFiniteMovers(player: Game.Player) : AVectorizedMovers(player) {
  override fun availableMoves(coords: Pair<Int, Int>): List<List<ClassicMoveDescription>> {
    val vectors = getVectors()
    val moves = mutableListOf<List<ClassicMoveDescription>>()
    for (vector in vectors) {
      val result = addVector(coords, vector)
      if (result != null) {
        moves.add(listOf(ClassicMoveDescription(coords, result)))
      }
    }
    return moves
  }

  override fun isMovePossible(move: ClassicMoveDescription): Boolean {
    return getVectors().contains(move.getVector())
  }
}
