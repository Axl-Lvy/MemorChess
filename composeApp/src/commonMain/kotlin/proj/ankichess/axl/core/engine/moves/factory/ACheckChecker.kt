package proj.ankichess.axl.core.engine.moves.factory

import proj.ankichess.axl.core.engine.moves.IMove
import proj.ankichess.axl.core.intf.engine.board.IPosition

abstract class ACheckChecker(position: IPosition) : AMoveFactory(position) {
  abstract fun isPossible(move: IMove): Boolean
}
