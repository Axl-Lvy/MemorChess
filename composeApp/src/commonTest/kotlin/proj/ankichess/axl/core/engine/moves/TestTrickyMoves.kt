package proj.ankichess.axl.core.engine.moves

import kotlin.test.Test
import kotlin.test.assertEquals
import proj.ankichess.axl.core.engine.Game
import proj.ankichess.axl.core.engine.board.IBoard
import proj.ankichess.axl.core.engine.moves.description.MoveDescription
import proj.ankichess.axl.core.engine.parser.FenParser

class TestTrickyMoves {
  @Test
  fun testKnightMoveConflictDifferentColumnAndRow() {
    val movesToSetUp = listOf("Nf3", "Nf6", "Nc3", "Nc6", "Ne4", "a6")
    val game = Game()
    for (move in movesToSetUp) {
      game.playMove(move)
    }
    val move = game.playMove(MoveDescription(IBoard.getCoords("f3"), IBoard.getCoords("g5")))
    assertEquals("Nfg5", move)
  }

  @Test
  fun testKnightMoveConflictSameColumn() {
    val movesToSetUp = listOf("e4", "a6", "Ne2", "a5", "Nbc3", "a4", "e5", "a3", "Ne4", "axb2")
    val game = Game()
    for (move in movesToSetUp) {
      game.playMove(move)
    }
    val move = game.playMove(MoveDescription(IBoard.getCoords("e2"), IBoard.getCoords("c3")))
    assertEquals("N2c3", move)
  }

  /**
   * In a very rare case, a player can have 3 pieces of the same type. If they can all go to the
   * same square, we need two clues.
   */
  @Test
  fun test3QueensNeed2Clues() {
    val game = FenParser.read("8/k7/4Q3/8/2Q1Q3/8/K7/8 w - - 0 1")
    val move = game.playMove(MoveDescription(IBoard.getCoords("e4"), IBoard.getCoords("c6")))
    assertEquals("Qe4c6", move)
  }
}
