package proj.ankichess.axl.core.game.moves.description

class ClassicMoveDescription(val from: Pair<Int, Int>, val to: Pair<Int, Int>) {

  fun getSubVector(): Pair<Int, Int> {
    val firstCoords = to.first - from.first
    val secondCoords = to.second - from.second
    val divider = gcd(firstCoords, secondCoords)
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
}
