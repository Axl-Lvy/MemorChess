package proj.ankichess.axl.core.game.moves

import kotlin.test.Test
import kotlin.test.assertEquals
import proj.ankichess.axl.core.game.Game
import proj.ankichess.axl.core.game.board.Board
import proj.ankichess.axl.core.game.pieces.IPiece

class TestKing : ATestPiece(IPiece.KING) {

  override fun getTiles(): List<String> {
    return listOf("a2", "h7", "a3", "h6", "b4", "g5", "c3", "f4")
  }

  @Test
  fun testLongCastle() {
    val board = Board()
    board.placePiece("e1", "K")
    board.placePiece("a1", "R")
    val game = Game(board)
    game.safePlayMove("O-O-O")
    assertEquals(IPiece.ROOK.uppercase(), game.board.getTile("d1").getSafePiece().toString())
    assertEquals(IPiece.KING.uppercase(), game.board.getTile("c1").getSafePiece().toString())
  }

  @Test
  fun testShortCastle() {
    val board = Board()
    board.placePiece("e1", "K")
    board.placePiece("h1", "R")
    val game = Game(board)
    game.safePlayMove("O-O")
    assertEquals(IPiece.ROOK.uppercase(), game.board.getTile("f1").getSafePiece().toString())
    assertEquals(IPiece.KING.uppercase(), game.board.getTile("g1").getSafePiece().toString())
  }
}
