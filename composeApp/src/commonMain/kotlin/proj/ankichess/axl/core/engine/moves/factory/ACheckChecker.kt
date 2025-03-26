package proj.ankichess.axl.core.engine.moves.factory

import proj.ankichess.axl.core.engine.board.Board
import proj.ankichess.axl.core.engine.moves.IMove

abstract class ACheckChecker(board: Board) : AMoveFactory(board) {
  abstract fun isPossible(move: IMove, enPassantColumn: Int): Boolean
}
