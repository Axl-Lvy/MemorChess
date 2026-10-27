package proj.memorchess.axl.core.engine

/** Utilities for converting board coordinates to human-readable square names (e.g. "e4"). */
object BoardUtils {
  private fun columnName(col: Int): String {
    return ('a' + col).toString()
  }

  /** Returns the algebraic name of the square at the given [row] and [col] (e.g. "a1", "h8"). */
  fun tileName(row: Int, col: Int): String {
    return columnName(col) + (row + 1)
  }

  /** Returns the algebraic name of the square at the given (row, col) pair. */
  fun tileName(coords: Pair<Int, Int>): String {
    return tileName(coords.first, coords.second)
  }

  /**
   * Returns the square a played [san] move lands on. [mover] resolves the castling literals, which
   * name no square directly.
   */
  fun destinationSquare(san: String, mover: Player): BoardLocation {
    val kingRow = if (mover == Player.WHITE) 0 else 7
    return when (san) {
      "O-O" -> BoardLocation(kingRow, 6)
      "O-O-O" -> BoardLocation(kingRow, 2)
      else -> {
        val core = san.substringBefore('=')
        val token = core.takeLast(2)
        BoardLocation(row = token[1] - '1', col = token[0] - 'a')
      }
    }
  }
}
