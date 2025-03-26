package proj.ankichess.axl.core.engine.moves.factory

import proj.ankichess.axl.core.engine.board.Board
import proj.ankichess.axl.core.engine.board.ITile

class SimpleMoveFactory(board: Board) : AMoveFactory(board) {
  override fun getTileAtCoords(coords: Pair<Int, Int>): ITile {
    return board.getTile(coords)
  }
}
