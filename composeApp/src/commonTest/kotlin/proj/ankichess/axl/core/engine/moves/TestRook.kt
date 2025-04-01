package proj.ankichess.axl.core.engine.moves

import proj.ankichess.axl.core.intf.engine.pieces.IPiece

class TestRook : ATestPiece(IPiece.ROOK) {

  override fun getTiles(): List<String> {
    return listOf("a7", "h1", "a3", "h6", "c3", "g6", "c4", "f6")
  }
}
