package proj.ankichess.axl.core.intf.engine.board

import proj.ankichess.axl.core.intf.engine.pieces.IPiece

interface ITile {
  fun getSafePiece(): IPiece?

  fun getCoords(): Pair<Int, Int>

  fun getColor(): TileColor

  enum class TileColor {
    WHITE,
    BLACK,
  }
}
