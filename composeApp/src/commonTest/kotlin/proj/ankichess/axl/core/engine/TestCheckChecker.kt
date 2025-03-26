package proj.ankichess.axl.core.engine

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import proj.ankichess.axl.core.engine.moves.IllegalMoveException

class TestCheckChecker {
  private lateinit var game: Game

  /** Initializes the board with an escapable check position. */
  @BeforeTest
  fun init() {
    game = Game()
    game.playMove("e4")
    game.playMove("f5")
    game.playMove("d4")
    game.playMove("d5")
    game.playMove("Qh5+")
  }

  @Test
  fun checkCannotMove() {

    assertFailsWith<IllegalMoveException>() {
      game.playMove("h6")
      print(game.board)
    }

    assertFailsWith<IllegalMoveException>() { game.playMove("Kf3") }
  }

  @Test
  fun testBlockCheck() {
    game.playMove("g6")
  }

  @Test
  fun testKingMoves() {
    game.playMove("Kd7")
  }
}
