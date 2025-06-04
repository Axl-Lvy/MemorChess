package proj.memorchess.axl.core.engine.board

import kotlin.test.Test

class TestSimpleOperations {
  @Test
  fun testPlacePiece() {
    val board = Board()
    board.placePiece("a1", "R")
  }
}
