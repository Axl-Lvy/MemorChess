package proj.ankichess.axl.core.engine.moves

import kotlin.test.*
import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.impl.engine.moves.IllegalMoveException
import proj.ankichess.axl.core.intf.engine.pieces.IPiece

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
    assertNull(game.position.board.getTile("g1").getSafePiece())
    assertEquals(
      IPiece.KNIGHT.uppercase(),
      game.position.board.getTile("f3").getSafePiece().toString(),
    )
  }
}
