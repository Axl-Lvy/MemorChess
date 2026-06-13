package proj.memorchess.axl.core.pgn

/**
 * Outcome of a successful PGN import, suitable for display in the UI.
 *
 * @property movesAdded Number of distinct moves that were inserted into the opening graph.
 * @property movesAlreadyPresent Number of distinct moves of the imported games that were already
 *   part of the user's repertoire and were therefore left untouched.
 */
data class PgnImportSummary(val movesAdded: Int, val movesAlreadyPresent: Int)
