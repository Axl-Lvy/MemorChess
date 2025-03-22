package proj.ankichess.axl.core.game.moves.factory

import proj.ankichess.axl.core.game.board.Board
import proj.ankichess.axl.core.game.board.ITile
import proj.ankichess.axl.core.game.moves.IMove

class NoCheckChecker() : ACheckChecker(Board()) {
  override fun isPossible(move: IMove, enPassantColumn: Int): Boolean {
    return true
  }

  override fun getTileAtCoords(coords: Pair<Int, Int>): ITile {
    throw UnsupportedOperationException()
  }
}
