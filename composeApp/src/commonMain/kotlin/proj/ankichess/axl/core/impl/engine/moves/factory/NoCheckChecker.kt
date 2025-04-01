package proj.ankichess.axl.core.impl.engine.moves.factory

import proj.ankichess.axl.core.impl.engine.board.Position
import proj.ankichess.axl.core.intf.engine.board.ITile
import proj.ankichess.axl.core.intf.engine.moves.IMove

class NoCheckChecker() : ACheckChecker(Position()) {
  override fun isPossible(move: IMove): Boolean {
    return true
  }

  override fun getTileAtCoords(coords: Pair<Int, Int>): ITile {
    throw UnsupportedOperationException()
  }
}
