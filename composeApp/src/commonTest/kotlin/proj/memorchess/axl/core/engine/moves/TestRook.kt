package proj.memorchess.axl.core.engine.moves

import proj.memorchess.axl.core.engine.pieces.Piece

class TestRook : PieceTester(Piece.ROOK) {

  override fun getTiles(): List<String> {
    return listOf("a7", "h1", "a3", "h6", "c3", "g6", "c4", "f6")
  }
}
