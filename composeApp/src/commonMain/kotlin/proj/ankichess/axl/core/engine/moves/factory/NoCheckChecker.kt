package proj.ankichess.axl.core.engine.moves.factory

import proj.ankichess.axl.core.engine.board.Board
import proj.ankichess.axl.core.engine.board.ITile
import proj.ankichess.axl.core.engine.moves.IMove

class NoCheckChecker() : ACheckChecker(Board()) {
  override fun isPossible(move: IMove, enPassantColumn: Int): Boolean {
    return true
  }

  override fun getTileAtCoords(coords: Pair<Int, Int>): ITile {
    throw UnsupportedOperationException()
  }
}
