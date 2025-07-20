package proj.memorchess.axl.core.engine.moves

import kotlin.test.Test
import kotlin.test.assertEquals
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.board.Board
import proj.memorchess.axl.core.engine.board.Position
import proj.memorchess.axl.core.engine.pieces.Piece

class TestKing : PieceTester(Piece.KING) {

  override fun getTiles(): List<String> {
    return listOf("a2", "h7", "a3", "h6", "b4", "g5", "c3", "f4")
  }

  @Test
  fun testLongCastle() {
    val board = Board()
    board.placePiece("e1", "K")
    board.placePiece("a1", "R")
    val game = Game(Position(board))
    game.playMove("O-O-O")
    assertEquals(
      Piece.ROOK.uppercase(),
      game.position.board.getTile("d1").getSafePiece().toString(),
    )
    assertEquals(
      Piece.KING.uppercase(),
      game.position.board.getTile("c1").getSafePiece().toString(),
    )
  }

  @Test
  fun testShortCastle() {
    val board = Board()
    board.placePiece("e1", "K")
    board.placePiece("h1", "R")
    val game = Game(Position(board))
    game.playMove("O-O")
    assertEquals(
      Piece.ROOK.uppercase(),
      game.position.board.getTile("f1").getSafePiece().toString(),
    )
    assertEquals(
      Piece.KING.uppercase(),
      game.position.board.getTile("g1").getSafePiece().toString(),
    )
  }
}
