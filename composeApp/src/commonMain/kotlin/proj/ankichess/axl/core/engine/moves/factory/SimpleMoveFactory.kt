package proj.ankichess.axl.core.engine.moves.factory

import proj.ankichess.axl.core.engine.board.ITile
import proj.ankichess.axl.core.engine.board.Position

class SimpleMoveFactory(position: Position) : AMoveFactory(position) {
  override fun getTileAtCoords(coords: Pair<Int, Int>): ITile {
    return position.board.getTile(coords)
  }
}
