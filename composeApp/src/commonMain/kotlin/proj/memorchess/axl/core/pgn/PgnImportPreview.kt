package proj.memorchess.axl.core.pgn

/**
 * Dry run overlap of a repertoire against the user's current graph.
 *
 * @property totalMoves Number of distinct moves the repertoire would import.
 * @property movesInCommon Moves already in the graph with the same classification, which an install
 *   leaves untouched.
 */
data class PgnImportPreview(val totalMoves: Int, val movesInCommon: Int)
