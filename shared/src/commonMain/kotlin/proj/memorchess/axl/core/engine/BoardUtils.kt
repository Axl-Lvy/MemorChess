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
   * Returns the square a played [san] move lands on. [whiteToMove] resolves the two castling
   * literals, which name no square directly; every other SAN shape (a plain move, a capture, a
   * disambiguated move, a promotion) ends in the destination square token, optionally followed by a
   * `=Q`/`=R`/etc. promotion suffix stripped before reading it.
   */
  fun destinationSquare(san: String, whiteToMove: Boolean): BoardLocation {
    val kingRow = if (whiteToMove) 0 else 7
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
