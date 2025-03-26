package proj.ankichess.axl.core.engine.moves

import proj.ankichess.axl.core.engine.pieces.IPiece

class TestBishop : ATestPiece(IPiece.BISHOP) {
  override fun getTiles(): List<String> {
    return listOf("b2", "c3", "a3", "e1", "d6", "g3")
  }
}
