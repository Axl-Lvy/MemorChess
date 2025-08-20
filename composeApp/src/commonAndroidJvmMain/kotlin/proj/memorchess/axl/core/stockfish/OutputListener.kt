package proj.memorchess.axl.core.stockfish

/** Listener to capture Stockfish async output. */
fun interface OutputListener {
  fun onOutput(output: String)
}
