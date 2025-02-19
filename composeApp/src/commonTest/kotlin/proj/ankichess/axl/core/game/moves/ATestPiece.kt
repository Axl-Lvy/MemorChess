package proj.ankichess.axl.core.game.moves

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import proj.ankichess.axl.core.game.Game
import proj.ankichess.axl.core.game.board.Board

abstract class ATestPiece(private val pieceName: String) {

  /** Moves of both black and white piece. White starts in a1 and black in h8. */
  abstract fun getTiles(): List<String>

  private lateinit var game: Game

  @BeforeTest
  fun setUp() {
    val board = Board()
    board.placePiece("a1", pieceName.uppercase())
    board.placePiece("h8", pieceName)
    game = Game(board)
  }

  @Test
  fun testMoves() {
    for (tile in getTiles()) {
      game.playMove(pieceName.uppercase() + tile)
    }
    checkFinalDestinations()
  }

  private fun checkFinalDestinations() {
    assertEquals(pieceName, game.board.getTile(getTiles().last()).toString())
    assertEquals(
      pieceName.uppercase(),
      game.board.getTile(getTiles()[getTiles().size - 2]).toString(),
    )
    game.board.getTilesIterator().forEach {
      val tileName = Board.getTileName(it.getCoords())
      if (tileName != getTiles()[getTiles().size - 2] && tileName != getTiles().last()) {
        assertEquals(null, it.getSafePiece())
      }
    }
  }

  @Test
  fun testCapture() {
    for (tile in getTiles()) {
      game.board.placePiece(tile, if (game.playerTurn == Game.Player.WHITE) "p" else "P")
      game.playMove(pieceName.uppercase() + "x" + tile)
    }
    checkFinalDestinations()
  }

  @Test
  fun testImpossibleMove() {
    assertFailsWith<IllegalStateException>("Found 0 possible moves with ka3") {
      game.playMove(pieceName.uppercase() + "b8")
    }
  }
}
