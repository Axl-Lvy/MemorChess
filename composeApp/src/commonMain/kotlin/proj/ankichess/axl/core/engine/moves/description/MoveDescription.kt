package proj.ankichess.axl.core.engine.moves.description

import kotlin.math.abs
import kotlin.math.max
import proj.ankichess.axl.core.engine.board.IBoard

class MoveDescription(val from: Pair<Int, Int>, val to: Pair<Int, Int>) {

  fun getSubVector(): Pair<Int, Int> {
    val firstCoords = to.first - from.first
    val secondCoords = to.second - from.second
    val divider = max(abs(gcd(firstCoords, secondCoords)), 1)
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
    return "MoveDescription[" + IBoard.getTileName(from) + "," + IBoard.getTileName(to) + "]"
  }
}
