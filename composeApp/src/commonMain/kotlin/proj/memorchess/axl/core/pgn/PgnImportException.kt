package proj.memorchess.axl.core.pgn

/**
 * Exception thrown when a PGN import cannot be applied to the opening graph.
 *
 * The main cause is a move that is illegal in the position it is played from. The import is
 * validated as a whole before anything is written, so when this exception is thrown the graph is
 * guaranteed to be untouched.
 */
class PgnImportException(message: String, cause: Throwable? = null) :
  IllegalArgumentException(message, cause)
