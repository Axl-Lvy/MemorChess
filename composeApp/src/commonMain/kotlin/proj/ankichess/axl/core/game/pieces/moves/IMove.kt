package proj.ankichess.axl.core.game.pieces.moves

import proj.ankichess.axl.core.game.board.Tile

interface IMove {

  fun destination(): Pair<Int, Int>

  fun origin(): Pair<Int, Int>

  fun play(boardArray: Array<Array<Tile>>)

  fun updateCaches(boardArray: Array<Array<Tile>>, cache: Map<String, MutableSet<Pair<Int, Int>>>)
}
