package proj.ankichess.axl.core.game.board

import proj.ankichess.axl.core.game.pieces.material.IPiece

interface ITile {
  fun getSafePiece(): IPiece?

  fun getCoords(): Pair<Int, Int>
}
