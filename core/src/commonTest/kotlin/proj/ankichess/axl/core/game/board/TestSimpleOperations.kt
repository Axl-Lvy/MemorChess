package proj.ankichess.axl.core.game.board

import kotlin.test.Test

class TestSimpleOperations {
  @Test
  fun testPlacePiece() {
    val board = Board()
    board.placePiece("a1", "R")
  }
}
