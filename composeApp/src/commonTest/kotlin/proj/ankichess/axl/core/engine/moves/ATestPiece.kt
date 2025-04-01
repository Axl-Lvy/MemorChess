package proj.ankichess.axl.core.engine.moves

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import proj.ankichess.axl.core.engine.Game
import proj.ankichess.axl.core.engine.board.Board
import proj.ankichess.axl.core.engine.board.Position
import proj.ankichess.axl.core.engine.moves.factory.NoCheckChecker
import proj.ankichess.axl.core.intf.engine.board.IBoard

/**
 * Simple and general tests for a piece. The game is initialized with just 2 pieces in a1 (white)
 * and h8 (black). We only need to create the path for each piece in [getTiles].
 *
 * @property pieceName The name of the piece.
 */
abstract class ATestPiece(private val pieceName: String) {

  /** Moves of both black and white piece. White starts in a1 and black in h8. */
  abstract fun getTiles(): List<String>

  private lateinit var game: Game

  @BeforeTest
  fun setUp() {
    val board = Board()
    board.placePiece("a1", pieceName.uppercase())
    board.placePiece("h8", pieceName)
    game = Game(Position(board), NoCheckChecker())
  }

  @Test
  fun testMoves() {
    for (tile in getTiles()) {
      game.playMove(pieceName.uppercase() + tile)
    }
    checkFinalDestinations()
  }

  private fun checkFinalDestinations() {
    assertEquals(pieceName, game.position.board.getTile(getTiles().last()).toString())
    assertEquals(
      pieceName.uppercase(),
      game.position.board.getTile(getTiles()[getTiles().size - 2]).toString(),
    )
    game.position.board.getTilesIterator().forEach {
      val tileName = IBoard.getTileName(it.getCoords())
      if (tileName != getTiles()[getTiles().size - 2] && tileName != getTiles().last()) {
        assertEquals(null, it.getSafePiece())
      }
    }
  }

  @Test
  fun testCapture() {
    for (tile in getTiles()) {
      game.position.board.placePiece(
        tile,
        if (game.position.playerTurn == Game.Player.WHITE) "p" else "P",
      )
      game.playMove(pieceName.uppercase() + "x" + tile)
    }
    checkFinalDestinations()
  }

  @Test
  fun testImpossibleMove() {
    assertFailsWith<IllegalMoveException>() { game.playMove(pieceName.uppercase() + "b8") }
  }
}
