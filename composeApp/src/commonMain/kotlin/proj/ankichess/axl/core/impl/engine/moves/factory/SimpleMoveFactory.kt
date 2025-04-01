package proj.ankichess.axl.core.impl.engine.moves.factory

import proj.ankichess.axl.core.intf.engine.board.IPosition
import proj.ankichess.axl.core.intf.engine.board.ITile

class SimpleMoveFactory(position: IPosition) : AMoveFactory(position) {
  override fun getTileAtCoords(coords: Pair<Int, Int>): ITile {
    return position.board.getTile(coords)
  }
}
