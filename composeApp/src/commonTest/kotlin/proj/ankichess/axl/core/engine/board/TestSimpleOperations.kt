package proj.ankichess.axl.core.engine.board

import kotlin.test.Test
import proj.ankichess.axl.core.impl.engine.board.Board

class TestSimpleOperations {
  @Test
  fun testPlacePiece() {
    val board = Board()
    board.placePiece("a1", "R")
  }
}
