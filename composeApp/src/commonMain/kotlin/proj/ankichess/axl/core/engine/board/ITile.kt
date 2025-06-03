package proj.ankichess.axl.core.engine.board

import proj.ankichess.axl.core.engine.pieces.IPiece

interface ITile {
  fun getSafePiece(): IPiece?

  fun getCoords(): Pair<Int, Int>

  fun getColor(): TileColor

  fun getName(): String

  enum class TileColor {
    WHITE,
    BLACK,
  }
}
