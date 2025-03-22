package proj.ankichess.axl.core.game.moves

import proj.ankichess.axl.core.game.Game
import proj.ankichess.axl.core.game.moves.description.MoveDescription
import proj.ankichess.axl.core.game.pieces.IPiece
import kotlin.test.*

class TestCannotJump {

  private lateinit var game: Game

  @BeforeTest
  fun setUp() {
    game = Game()
  }

  @Test
  fun testBishop() {
    assertFailsWith<IllegalStateException>() {
      game.playMove("B" + "a3")
    }
  }

  @Test
  fun testRook() {
    assertFailsWith<IllegalStateException>() {
      game.playMove("R" + "a3")
    }
  }

  @Test
  fun testQueen() {
    assertFailsWith<IllegalStateException>() {
      game.playMove("Q" + "d3")
    }
  }

  @Test
  fun testKnight() {
    game.playMove("N" + "f3")
    assertNull(game.board.getTile("g1").getSafePiece())
    assertEquals(IPiece.KNIGHT.uppercase(), game.board.getTile("f3").getSafePiece().toString())
  }
}
