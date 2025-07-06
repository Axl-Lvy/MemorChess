package proj.memorchess.axl.core.engine.moves

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.board.Board
import proj.memorchess.axl.core.engine.board.Position
import proj.memorchess.axl.core.engine.moves.factory.NoCheckChecker
import proj.memorchess.axl.core.engine.pieces.IPiece
import proj.memorchess.axl.core.engine.pieces.vectors.Queen
import proj.memorchess.axl.core.engine.pieces.vectors.Rook

class TestPawn {
  @Test
  fun testForwardMoves() {
    val board = Board()
    board.placePiece("a2", "P") // White pawn
    board.placePiece("b7", "p")
    val game = Game(Position(board), NoCheckChecker())
    println(game.position.board)
    game.playMove("a3")
    println(game.position.board)
    assertEquals(
      IPiece.PAWN.uppercase(),
      game.position.board.getTile("a3").getSafePiece().toString(),
    )

    game.playMove("b5")
    assertEquals(IPiece.PAWN, game.position.board.getTile("b5").getSafePiece().toString())
  }

  @Test
  fun testEnPassant() {
    val board = Board()
    board.placePiece("b4", "p") // Black pawn
    board.placePiece("a2", "P") // White pawn to capture
    val game = Game(Position(board), NoCheckChecker())

    game.playMove("a4")
    game.playMove("bxa3")
    assertEquals(IPiece.PAWN, game.position.board.getTile("a3").getSafePiece().toString())
    assertEquals(null, game.position.board.getTile("a4").getSafePiece())
    assertEquals(null, game.position.board.getTile("b4").getSafePiece())
  }

  @Test
  fun testCapture() {
    val board = Board()
    board.placePiece("e5", "P")
    board.placePiece("d6", "p")
    val game = Game(Position(board), NoCheckChecker())

    game.playMove("exd6")
    assertEquals(
      IPiece.PAWN.uppercase(),
      game.position.board.getTile("d6").getSafePiece().toString(),
    )
    assertEquals(null, game.position.board.getTile("d5").getSafePiece())
  }

  @Test
  fun testPromotionQueen() {
    testPromotion(Queen.white())
  }

  @Test
  fun testPromotionRook() {
    testPromotion(Rook.white())
  }

  private fun testPromotion(piece: IPiece) {
    val board = Board()
    board.placePiece("h7", "P")
    val game = Game(Position(board), NoCheckChecker())
    game.playMove("h8=$piece")
    assertEquals(
      piece.toString().uppercase(),
      game.position.board.getTile("h8").getSafePiece().toString(),
    )
  }

  @Test
  fun testImpossibleMove() {
    val board = Board()
    board.placePiece("a2", "P") // White pawn
    val game = Game(Position(board), NoCheckChecker())

    assertFailsWith<IllegalMoveException>() {
      game.playMove("a5") // Cannot jump two squares from a2 directly to a5
    }
  }
}
