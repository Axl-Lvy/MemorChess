package proj.memorchess.axl.core.stockfish

/**
 * Additional commands added by JStockfish, independent of the current game, can be called any time.
 */
class Position {
  companion object {
    init {
      System.loadLibrary("jstockfish")
    }

    private external fun whiteScores(chess960: Boolean, position: String): DoubleArray

    /** Evaluates a UCI position. */
    fun scores(chess960: Boolean, position: String): Scores {
      return Scores(whiteScores(chess960, position))
    }

    /**
     * Given a UCI position (like "startpos moves d2d4") and a move (like "g8f6"), checks if the
     * move is valid.
     */
    external fun isLegal(chess960: Boolean, position: String, move: String): Boolean

    /**
     * Converts a UCI position (like "startpos moves d2d4") to FEN (like
     * "rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq - 0 1").
     */
    external fun fen(chess960: Boolean, position: String): String

    private external fun ordinalState(chess960: Boolean, position: String): Int

    /** Returns [State] of a UCI position. */
    fun state(chess960: Boolean, position: String): State {
      val ordinal = ordinalState(chess960, position)
      return State.values()[ordinal]
    }
  }
}
