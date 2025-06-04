package proj.memorchess.axl.test_util

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
