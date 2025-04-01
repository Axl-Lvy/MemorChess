package proj.ankichess.axl.core.impl.engine.moves.factory

import proj.ankichess.axl.core.intf.engine.board.IPosition
import proj.ankichess.axl.core.intf.engine.moves.IMove

abstract class ACheckChecker(position: IPosition) : AMoveFactory(position) {
  abstract fun isPossible(move: IMove): Boolean
}
