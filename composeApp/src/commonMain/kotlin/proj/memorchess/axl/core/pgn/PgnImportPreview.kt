package proj.memorchess.axl.core.pgn

/**
 * Dry run overlap of a repertoire against the user's current graph.
 *
 * @property totalMoves Number of distinct moves the repertoire would import.
 * @property movesInCommon Moves already in the graph with the same classification, which an install
 *   leaves untouched.
 * @property movesToDelete Moves an update deletes because the new version dropped them and nothing
 *   else holds them; `0` for a first install.
 */
data class PgnImportPreview(val totalMoves: Int, val movesInCommon: Int, val movesToDelete: Int = 0)
