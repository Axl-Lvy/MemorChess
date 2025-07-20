package proj.memorchess.axl.core.engine.moves.factory

import proj.memorchess.axl.core.engine.board.ITile
import proj.memorchess.axl.core.engine.board.Position
import proj.memorchess.axl.core.engine.moves.Move

class NoCheckChecker : CheckChecker(Position()) {
  override fun isPossible(move: Move): Boolean {
    return true
  }

  override fun getTileAtCoords(coords: Pair<Int, Int>): ITile {
    throw UnsupportedOperationException()
  }
}
