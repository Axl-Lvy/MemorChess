package proj.ankichess.axl.core.game.moves.description

import kotlin.math.abs
import proj.ankichess.axl.core.game.board.Board

class MoveDescription(val from: Pair<Int, Int>, val to: Pair<Int, Int>) {

  fun getSubVector(): Pair<Int, Int> {
    val firstCoords = to.first - from.first
    val secondCoords = to.second - from.second
    val divider = abs(gcd(firstCoords, secondCoords))
    return Pair(firstCoords / divider, secondCoords / divider)
  }

  fun getVector(): Pair<Int, Int> {
    val firstCoords = to.first - from.first
    val secondCoords = to.second - from.second
    return Pair(firstCoords, secondCoords)
  }

  companion object {
    private fun gcd(a: Int, b: Int): Int {
      if (b == 0) {
        return a
      }
      return gcd(b, a % b)
    }
  }

  override fun toString(): String {
    return "MoveDescription[" + Board.getTileName(from) + "," + Board.getTileName(to) + "]"
  }
}
