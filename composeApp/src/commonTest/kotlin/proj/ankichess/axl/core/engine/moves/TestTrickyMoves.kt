package proj.ankichess.axl.core.engine.moves

import kotlin.test.Test
import kotlin.test.assertEquals
import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.impl.engine.moves.description.MoveDescription
import proj.ankichess.axl.core.intf.engine.board.IBoard

class TestTrickyMoves {
  @Test
  fun testKnightMoveColumnConflict() {
    val movesToSetUp = listOf("Nf3", "Nf6", "Nc3", "Nc6", "Ne4", "a6")
    val game = Game()
    for (move in movesToSetUp) {
      game.playMove(move)
    }
    val move = game.playMove(MoveDescription(IBoard.getCoords("f3"), IBoard.getCoords("g5")))
    assertEquals("Nfg5", move)
  }
}
