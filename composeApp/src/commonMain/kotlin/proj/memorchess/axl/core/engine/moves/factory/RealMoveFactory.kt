package proj.memorchess.axl.core.engine.moves.factory

import proj.memorchess.axl.core.engine.board.IPosition
import proj.memorchess.axl.core.engine.board.ITile

/** A [MoveFactory] that create moves from the real piece positions on the board. */
class RealMoveFactory(position: IPosition) : MoveFactory(position) {
  override fun getTileAtCoords(coords: Pair<Int, Int>): ITile {
    return position.board.getTile(coords)
  }
}
