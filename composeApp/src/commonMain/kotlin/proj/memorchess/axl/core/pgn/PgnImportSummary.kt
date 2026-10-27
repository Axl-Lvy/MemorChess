package proj.memorchess.axl.core.pgn

/**
 * Outcome of a successful PGN import.
 *
 * @property movesAdded Distinct moves inserted into the opening graph.
 * @property movesAlreadyPresent Distinct imported moves already in the repertoire, left untouched.
 */
data class PgnImportSummary(val movesAdded: Int, val movesAlreadyPresent: Int)
