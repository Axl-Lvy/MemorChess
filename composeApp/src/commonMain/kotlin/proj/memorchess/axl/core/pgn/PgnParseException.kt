package proj.memorchess.axl.core.pgn

/**
 * Exception thrown when a PGN document cannot be parsed.
 *
 * Every parsing failure (unbalanced parentheses, unterminated comments, malformed tag pairs,
 * invalid SAN tokens, misplaced variations) is reported through this single exception type.
 *
 * @property line the 1-based line where the problem was detected.
 * @property column the 1-based column where the problem was detected.
 */
class PgnParseException(message: String, val line: Int, val column: Int) :
  IllegalArgumentException("$message (line $line, column $column)")
