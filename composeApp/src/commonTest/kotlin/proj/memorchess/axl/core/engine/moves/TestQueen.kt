package proj.memorchess.axl.core.engine.moves

import proj.memorchess.axl.core.engine.pieces.IPiece

class TestQueen : ATestPiece(IPiece.QUEEN) {

  override fun getTiles(): List<String> {
    return listOf("a7", "h1", "a3", "h6", "c5", "g5", "c3", "f4")
  }
}
