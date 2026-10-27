package proj.memorchess.axl.core.engine

/** A square on the chess board identified by [row] (0 = rank 1) and [col] (0 = file a). */
data class BoardLocation(val row: Int, val col: Int) {
  val color: TileColor = if ((row + col) % 2 == 0) TileColor.BLACK else TileColor.WHITE
}
