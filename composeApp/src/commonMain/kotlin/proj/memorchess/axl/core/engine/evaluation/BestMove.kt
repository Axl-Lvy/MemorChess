package proj.memorchess.axl.core.engine.evaluation

import proj.memorchess.axl.core.engine.BoardLocation

/**
 * The engine's recommended move, parsed from UCI notation.
 *
 * @property from Source square.
 * @property to Destination square.
 */
data class BestMove(val from: BoardLocation, val to: BoardLocation) {
  companion object {
    private const val MIN_UCI_LENGTH = 4

    /**
     * Parses a UCI move string (e.g. "e2e4") into a [BestMove].
     *
     * Promotion suffixes (e.g. "e7e8q") are ignored — only the first four characters are used.
     *
     * @return The parsed move, or `null` if the string is malformed.
     */
    fun fromUci(uci: String): BestMove? {
      if (uci.length < MIN_UCI_LENGTH) return null
      val fromCol = uci[0] - 'a'
      val fromRow = uci[1] - '1'
      val toCol = uci[2] - 'a'
      val toRow = uci[3] - '1'
      if (fromCol !in 0..7 || fromRow !in 0..7 || toCol !in 0..7 || toRow !in 0..7) return null
      return BestMove(BoardLocation(fromRow, fromCol), BoardLocation(toRow, toCol))
    }
  }
}
