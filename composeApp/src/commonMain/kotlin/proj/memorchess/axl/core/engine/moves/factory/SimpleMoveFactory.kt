package proj.memorchess.axl.core.engine.moves.factory

import proj.memorchess.axl.core.engine.board.IPosition
import proj.memorchess.axl.core.engine.board.ITile

class SimpleMoveFactory(position: IPosition) : AMoveFactory(position) {
  override fun getTileAtCoords(coords: Pair<Int, Int>): ITile {
    return position.board.getTile(coords)
  }
}
