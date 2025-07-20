package proj.memorchess.axl.core.engine.moves

import proj.memorchess.axl.core.engine.pieces.Piece

class TestBishop : PieceTester(Piece.BISHOP) {
  override fun getTiles(): List<String> {
    return listOf("b2", "c3", "a3", "e1", "d6", "g3")
  }
}
