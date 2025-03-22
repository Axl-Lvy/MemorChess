package proj.ankichess.axl.core.game.moves

import kotlin.test.*
import proj.ankichess.axl.core.game.Game
import proj.ankichess.axl.core.game.pieces.IPiece

class TestCannotJump {

  private lateinit var game: Game

  @BeforeTest
  fun setUp() {
    game = Game()
  }

  @Test
  fun testBishop() {
    assertFailsWith<IllegalMoveException>() { game.playMove("Ba3") }
  }

  @Test
  fun testRook() {
    assertFailsWith<IllegalMoveException>() { game.playMove("Ra3") }
  }

  @Test
  fun testQueen() {
    assertFailsWith<IllegalMoveException>() { game.playMove("Qd3") }
  }

  @Test
  fun testKnight() {
    game.playMove("Nf3")
    assertNull(game.board.getTile("g1").getSafePiece())
    assertEquals(IPiece.KNIGHT.uppercase(), game.board.getTile("f3").getSafePiece().toString())
  }
}
