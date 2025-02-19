package proj.ankichess.axl.core.game.moves

import proj.ankichess.axl.core.game.pieces.material.IPiece

class TestKnight : ATestPiece(IPiece.KNIGHT) {

  override fun getTiles(): List<String> {
    return listOf("b3", "g6", "d2", "f4", "c4", "h3")
  }
}
