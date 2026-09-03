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
}
