package proj.ankichess.axl.core.engine.moves.factory

import proj.ankichess.axl.core.engine.board.IPosition
import proj.ankichess.axl.core.engine.moves.IMove

abstract class ACheckChecker(position: IPosition) : AMoveFactory(position) {
  abstract fun isPossible(move: IMove): Boolean
}
