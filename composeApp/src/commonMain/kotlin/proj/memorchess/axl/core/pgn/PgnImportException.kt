package proj.memorchess.axl.core.pgn

/**
 * Thrown when a PGN import cannot be applied to the opening graph, which is then left untouched.
 */
class PgnImportException(message: String, cause: Throwable? = null) :
  IllegalArgumentException(message, cause)
