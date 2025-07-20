package proj.memorchess.axl.core.engine.moves

import kotlin.test.*
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.Piece

class TestCannotJump {

  private lateinit var game: Game

  @BeforeTest
  fun setUp() {
    game = Game()
  }

  @Test
  fun testBishop() {
    assertFailsWith<IllegalMoveException> { game.playMove("Ba3") }
  }

  @Test
  fun testRook() {
    assertFailsWith<IllegalMoveException> { game.playMove("Ra3") }
  }

  @Test
  fun testQueen() {
    assertFailsWith<IllegalMoveException> { game.playMove("Qd3") }
  }

  @Test
  fun testKnight() {
    game.playMove("Nf3")
    assertNull(game.position.board.getTile("g1").getSafePiece())
    assertEquals(
      Piece.KNIGHT.uppercase(),
      game.position.board.getTile("f3").getSafePiece().toString(),
    )
  }
}
