package proj.ankichess.axl.core.engine.moves.factory

import proj.ankichess.axl.core.engine.board.Position
import proj.ankichess.axl.core.engine.moves.IMove

abstract class ACheckChecker(position: Position) : AMoveFactory(position) {
  abstract fun isPossible(move: IMove): Boolean
}
