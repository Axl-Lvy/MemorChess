package proj.ankichess.axl.core.game.moves

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import proj.ankichess.axl.core.game.Game
import proj.ankichess.axl.core.game.board.Board
import proj.ankichess.axl.core.game.pieces.IPiece

class TestPawn {
  @Test
  fun testForwardMoves() {
    val board = Board()
    board.placePiece("a2", "P") // White pawn
    board.placePiece("b7", "p")
    val game = Game(board)
    println(game.board)
    game.playMove("a3")
    println(game.board)
    assertEquals(IPiece.PAWN.uppercase(), game.board.getTile("a3").getSafePiece().toString())

    game.playMove("b5")
    assertEquals(IPiece.PAWN, game.board.getTile("b5").getSafePiece().toString())
  }

  @Test
  fun testEnPassant() {
    val board = Board()
    board.placePiece("b4", "p") // Black pawn
    board.placePiece("a2", "P") // White pawn to capture
    val game = Game(board)

    game.playMove("a4")
    game.playMove("bxa3")
    assertEquals(IPiece.PAWN, game.board.getTile("a3").getSafePiece().toString())
    assertEquals(null, game.board.getTile("a4").getSafePiece())
    assertEquals(null, game.board.getTile("b4").getSafePiece())
  }

  @Test
  fun testCapture() {
    val board = Board()
    board.placePiece("e5", "P")
    board.placePiece("d6", "p")
    val game = Game(board)

    game.playMove("exd6")
    assertEquals(IPiece.PAWN.uppercase(), game.board.getTile("d6").getSafePiece().toString())
    assertEquals(null, game.board.getTile("d5").getSafePiece())
  }

  @Test
  fun testPromotion() {
    // TODO
  }

  @Test
  fun testImpossibleMove() {
    val board = Board()
    board.placePiece("a2", "P") // White pawn
    val game = Game(board)

    assertFailsWith<IllegalStateException>("Move should be impossible") {
      game.playMove("a5") // Cannot jump two squares from a2 directly to a5
    }
  }
}
