package proj.memorchess.axl.core.pgn

/**
 * Thrown when a PGN document cannot be parsed.
 *
 * @property line the 1-based line where the problem was detected.
 * @property column the 1-based column where the problem was detected.
 */
class PgnParseException(message: String, val line: Int, val column: Int) :
  IllegalArgumentException("$message (line $line, column $column)")
