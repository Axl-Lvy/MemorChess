package proj.memorchess.axl.core.engine.moves

import proj.memorchess.axl.core.engine.pieces.Piece

class TestKnight : PieceTester(Piece.KNIGHT) {

  override fun getTiles(): List<String> {
    return listOf("b3", "g6", "d2", "f4", "c4", "h3")
  }
}
