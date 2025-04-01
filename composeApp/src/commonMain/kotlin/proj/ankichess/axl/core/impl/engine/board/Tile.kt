package proj.ankichess.axl.core.impl.engine.board

import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.intf.engine.board.ITile
import proj.ankichess.axl.core.intf.engine.pieces.IPiece

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

  override fun toString(): String {
    val pieceString = piece?.toString() ?: " "
    return if (piece?.player == Game.Player.WHITE) pieceString.uppercase() else pieceString
  }
}
