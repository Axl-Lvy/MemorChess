package proj.memorchess.axl.core.engine.board

import proj.memorchess.axl.core.engine.pieces.Piece

interface ITile {
  fun getSafePiece(): Piece?

  fun getCoords(): Pair<Int, Int>

  fun getColor(): TileColor

  fun getName(): String

  enum class TileColor {
    WHITE,
    BLACK,
  }
}
