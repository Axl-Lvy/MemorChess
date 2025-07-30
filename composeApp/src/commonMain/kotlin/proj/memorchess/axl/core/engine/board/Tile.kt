package proj.memorchess.axl.core.engine.board

import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.Piece

/**
 * Mutable representation of a tile.
 *
 * @property gridItem GridItem of this tile.
 * @property getSafePiece Piece on this tile.
 * @constructor Create empty Tile.
 */
class Tile(override val gridItem: GridItem, var piece: Piece?) : ITile {

  constructor(row: Int, col: Int) : this(GridItem(row, col), null)

  /** Remove the piece on this tile. */
  fun reset() {
    piece = null
  }

  override fun getSafePiece(): Piece? {
    return piece
  }

  override fun getCoords(): Pair<Int, Int> {
    return Pair(gridItem.row, gridItem.col)
  }

  override fun getColor(): ITile.TileColor {
    return gridItem.color
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

    if (gridItem != other.gridItem) return false
    if (piece != other.piece) return false

    return true
  }

  override fun hashCode(): Int {
    var result = gridItem.hashCode()
    result = 31 * result + (piece?.hashCode() ?: 0)
    return result
  }
}
