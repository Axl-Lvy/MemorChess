package proj.ankichess.axl.core.game.moves

import proj.ankichess.axl.core.game.pieces.IPiece

class TestRook : ATestPiece(IPiece.ROOK) {

  override fun getTiles(): List<String> {
    return listOf("a7", "h1", "a3", "h6", "c3", "g6", "c4", "f6")
  }
}
