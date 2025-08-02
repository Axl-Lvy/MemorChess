package proj.memorchess.axl.core.engine.board

/**
 * Represents a grid item on the chessboard, i.e a tile without the piece.
 *
 * @property row row of the grid item
 * @property col column of the grid item
 */
data class GridItem(val row: Int, val col: Int) {
  val color: ITile.TileColor =
    if ((row + col) % 2 == 0) ITile.TileColor.BLACK else ITile.TileColor.WHITE
}
