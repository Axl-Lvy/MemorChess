package proj.memorchess.axl.core.stockfish

/**
 * This class contains commands that mimic UCI commands.
 *
 * <p>{@link #go}, {@link #ponderhit}, {@link #bench}, and {@link #perft} are async. Use
 * {@link #setOutputListener} to capture Stockfish output; by default Stockfish output is printed to
 * stdout. Other methods are sync, they return results immediately.
 *
 * <p>Stockfish can't process multiple games at a time. User of this class must handle
 * synchronization.
 */
class UCI {
  companion object {
    init {
      System.loadLibrary("jstockfish")
    }

    // By default, just print to stdout
    private var listener: OutputListener =
      object : OutputListener {
        override fun onOutput(output: String) {
          println(output)
        }
      }

    /** Only one listener is supported. */
    fun setOutputListener(listener: OutputListener) {
      this.listener = listener
    }

    /** Called by native code. This method will in turn call the listener. */
    @JvmStatic
    private fun onOutput(output: String) {
      listener.onOutput(output)
    }

    // Standard UCI commands ---------------------------------------------------
    // - debug: unavailable because Stockfish always outputs "info"
    // - isready: unavailable this lib is always ready

    /** Returns UCI id and options. Call this method before anything else. */
    external fun uci(): String

    /** Returns `false` if the option name is incorrect. */
    external fun setOption(name: String, value: String): Boolean

    /** Call this method should be called if a call to position or go is from a different game. */
    external fun newGame()

    /** Returns `false` if the position (like "startpos moves d2d4") is invalid. */
    external fun position(position: String): Boolean

    /** Searches for the best move. */
    external fun go(options: String)

    /** Stops when `go("infinite")` is called. */
    external fun stop()

    external fun ponderHit()

    // Additional commands added by Stockfish ----------------------------------

    /** Prints (visualize) the current position. */
    external fun d(): String

    /** Flips the current position. */
    external fun flip()

    /** Evaluates the current position. */
    external fun eval(): String

    // Additional commands added by JStockfish ---------------------------------

    private external fun whiteScores(): DoubleArray

    /** Evaluates the current position. */
    fun scores(): Scores {
      return Scores(whiteScores())
    }

    /**
     * Given the current position (set by [position]) and a move (like "g8f6"), checks if the move
     * is valid.
     */
    external fun isLegal(move: String): Boolean

    /**
     * Converts the current position (set by [position]) to FEN (like
     * "rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq - 0 1").
     */
    external fun fen(): String

    private external fun ordinalState(): Int

    /** Returns [State] of the current position (set by [position]). */
    fun state(): State {
      val ordinal = ordinalState()
      return State.values()[ordinal]
    }
  }
}
