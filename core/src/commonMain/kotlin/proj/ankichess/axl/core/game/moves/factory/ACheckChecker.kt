package proj.ankichess.axl.core.game.moves.factory

import proj.ankichess.axl.core.game.board.Board
import proj.ankichess.axl.core.game.moves.IMove

abstract class ACheckChecker(board: Board) : AMoveFactory(board) {
  abstract fun isPossible(move: IMove, enPassantColumn: Int): Boolean
}
