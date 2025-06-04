package proj.memorchess.axl.core.engine.board

import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.IPiece

/**
 * Tile.
 *
 * @property row Row of this tile.
 * @property col Column of this tile.
 * @property getSafePiece Piece on this tile.
 * @constructor Create empty Tile.
 */
class Tile(private val row: Int, private val col: Int, var piece: IPiece?) : ITile {

  constructor(row: Int, col: Int) : this(row, col, null)

  private val color: ITile.TileColor =
    if ((row + col) % 2 == 0) ITile.TileColor.BLACK else ITile.TileColor.WHITE

  /** Remove the piece on this tile. */
  fun reset() {
    piece = null
  }

  override fun getSafePiece(): IPiece? {
    return piece
  }

  override fun getCoords(): Pair<Int, Int> {
    return Pair(row, col)
  }

  override fun getColor(): ITile.TileColor {
    return color
  }

  override fun getName(): String {
    return IBoard.getTileName(getCoords())
  }

  override fun toString(): String {
    val pieceString = piece?.toString() ?: " "
    return if (piece?.player == Game.Player.WHITE) pieceString.uppercase() else pieceString
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Tile) return false

    if (row != other.row) return false
    if (col != other.col) return false
    if (piece != other.piece) return false

    return true
  }

  override fun hashCode(): Int {
    var result = row
    result = 31 * result + col
    result = 31 * result + (piece?.hashCode() ?: 0)
    return result
  }
}
