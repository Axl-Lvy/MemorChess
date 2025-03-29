package proj.ankichess.axl.core.engine.moves.factory

import proj.ankichess.axl.core.engine.board.ITile
import proj.ankichess.axl.core.engine.board.Position
import proj.ankichess.axl.core.engine.moves.IMove

class NoCheckChecker() : ACheckChecker(Position()) {
  override fun isPossible(move: IMove): Boolean {
    return true
  }

  override fun getTileAtCoords(coords: Pair<Int, Int>): ITile {
    throw UnsupportedOperationException()
  }
}
