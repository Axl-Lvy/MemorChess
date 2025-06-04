package proj.memorchess.axl.core.engine.moves.factory

import proj.memorchess.axl.core.engine.board.ITile
import proj.memorchess.axl.core.engine.board.Position
import proj.memorchess.axl.core.engine.moves.IMove

class NoCheckChecker() : ACheckChecker(Position()) {
  override fun isPossible(move: IMove): Boolean {
    return true
  }

  override fun getTileAtCoords(coords: Pair<Int, Int>): ITile {
    throw UnsupportedOperationException()
  }
}
