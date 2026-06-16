package proj.memorchess.axl.test_util

import kotlin.time.Duration.Companion.seconds

/**
 * Get button description according to string resource.
 *
 * @param buttonName Name of the button to get description for.
 */
fun getNavigationButtonDescription(buttonName: String): String {
  return "$buttonName button"
}

/**
 * Get tile description according to string resource.
 *
 * @param tileName Name of the tile to get description for.
 */
fun getTileDescription(tileName: String): String {
  return "Tile $tileName"
}

/**
 * Get next move button description according to string resource.
 *
 * @param move Name of the button to get description for.
 */
fun getNextMoveDescription(move: String): String {
  return "Play $move"
}

/**
 * Get a piece description according to string resource.
 *
 * @param piece Name of the piece to get description for.
 */
fun getPieceDescription(piece: String): String {
  return "Piece $piece"
}

val TEST_TIMEOUT = 10.seconds

/**
 * Safety ceiling for a page that loads its board asynchronously (e.g. the repertoire viewer, which
 * fetches and parses a PGN in a `LaunchedEffect` before the board renders) to finish settling. The
 * load itself completes quickly once the poll loop yields to the event loop; this is generously
 * larger than [TEST_TIMEOUT] only so a genuinely slow host does not trip it spuriously.
 */
val BOARD_LOAD_TIMEOUT = 30.seconds
