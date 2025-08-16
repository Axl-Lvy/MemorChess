package proj.memorchess.axl.core.engine.board

import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.Piece

/**
 * Mutable representation of a tile.
 *
 * @property boardLocation GridItem of this tile.
 * @property getSafePiece Piece on this tile.
 * @constructor Create empty Tile.
 */
class Tile(override val boardLocation: BoardLocation, var piece: Piece?) : ITile {

  constructor(row: Int, col: Int) : this(BoardLocation(row, col), null)

  /** Remove the piece on this tile. */
  fun reset() {
    piece = null
  }

  override fun getSafePiece(): Piece? {
    return piece
  }

  override fun getCoords(): Pair<Int, Int> {
    return Pair(boardLocation.row, boardLocation.col)
  }

  override fun getColor(): ITile.TileColor {
    return boardLocation.color
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

    if (boardLocation != other.boardLocation) return false
    if (piece != other.piece) return false

    return true
  }

  override fun hashCode(): Int {
    var result = boardLocation.hashCode()
    result = 31 * result + (piece?.hashCode() ?: 0)
    return result
  }
}
