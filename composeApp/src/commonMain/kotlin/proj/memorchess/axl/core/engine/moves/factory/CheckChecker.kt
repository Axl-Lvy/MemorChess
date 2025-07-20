package proj.memorchess.axl.core.engine.moves.factory

import proj.memorchess.axl.core.engine.board.IPosition
import proj.memorchess.axl.core.engine.moves.Move

abstract class CheckChecker(position: IPosition) : MoveFactory(position) {
  abstract fun isPossible(move: Move): Boolean
}
