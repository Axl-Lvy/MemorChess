package proj.ankichess.axl.core.game.pieces.material

import proj.ankichess.axl.core.game.Game
import proj.ankichess.axl.core.game.pieces.moves.description.ClassicMoveDescription

/** Piece that can move infinitely by adding multiple times [its vectors][getVectors]. */
abstract class AInfiniteMovers(player: Game.Player) : AVectorizedMovers(player) {
  override fun availableMoves(coords: Pair<Int, Int>): List<List<ClassicMoveDescription>> {
    val vectors = getVectors()
    val moves = mutableListOf<List<ClassicMoveDescription>>()
    for (vector in vectors) {
      val moveLine = mutableListOf<ClassicMoveDescription>()
      var result = addVector(coords, vector)
      while (result != null) {
        moveLine.add(ClassicMoveDescription(coords, result))
        result = addVector(result, vector)
      }
      if (moveLine.isNotEmpty()) {
        moves.add(moveLine)
      }
    }
    return moves
  }

  override fun isMovePossible(move: ClassicMoveDescription): Boolean {
    return getVectors().contains(move.getSubVector())
  }
}
