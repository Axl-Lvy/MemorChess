package proj.ankichess.axl.core.game.board

import proj.ankichess.axl.core.game.Game
import proj.ankichess.axl.core.game.board.ITile
import proj.ankichess.axl.core.game.pieces.material.IPiece

/**
 * Tile.
 *
 * @property row Row of this tile.
 * @property col Column of this tile.
 * @property getSafePiece Piece on this tile.
 * @constructor Create empty Tile.
 */
class Tile(private val row: Int, private val col: Int, var piece: IPiece?) : ITile {

  /** Remove the piece on this tile. */
  fun reset() {
    piece = null
  }

  constructor(row: Int, col: Int) : this(row, col, null)

  enum class TileColor {
    WHITE,
    BLACK,
  }

  override fun getSafePiece(): IPiece? {
    return piece
  }

  override fun getCoords(): Pair<Int, Int> {
    return Pair(row, col)
  }

  override fun toString(): String {
    val pieceString = piece?.toString() ?: " "
    return if (piece?.player == Game.Player.WHITE) pieceString.uppercase() else pieceString
  }
}
