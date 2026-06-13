package proj.memorchess.axl.core.pgn

/**
 * Dry run overlap of a repertoire against the user's current graph, computed without writing
 * anything. Lets the UI tell the user how much of a repertoire they already know before installing.
 *
 * @property totalMoves Number of distinct moves the repertoire would import.
 * @property movesInCommon How many of those moves are already part of the user's graph with the
 *   same classification, and would therefore be left untouched by an install.
 */
data class PgnImportPreview(val totalMoves: Int, val movesInCommon: Int)
