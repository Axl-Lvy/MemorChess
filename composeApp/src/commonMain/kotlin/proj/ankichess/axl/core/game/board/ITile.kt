package proj.ankichess.axl.core.game.board

import proj.ankichess.axl.core.game.pieces.IPiece

interface ITile {
  fun getSafePiece(): IPiece?

  fun getCoords(): Pair<Int, Int>
}
