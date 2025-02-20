package proj.ankichess.axl.core.game.moves.factory

import proj.ankichess.axl.core.game.board.Board
import proj.ankichess.axl.core.game.board.ITile

class SimpleMoveFactory(board: Board) : AMoveFactory(board) {
  override fun getTileAtCoords(coords: Pair<Int, Int>): ITile {
    return board.getTile(coords)
  }
}
