package proj.ankichess.axl.core.game.pieces.moves

import proj.ankichess.axl.core.game.board.Tile

open class ClassicMove(val from: Pair<Int, Int>, val to: Pair<Int, Int>) : IMove {

  override fun destination(): Pair<Int, Int> {
    return to
  }

  override fun origin(): Pair<Int, Int> {
    return from
  }

  override fun play(boardArray: Array<Array<Tile>>) {
    val fromTile = boardArray[from.first][from.second]
    val toTile = boardArray[to.first][to.second]
    toTile.piece = fromTile.piece
    fromTile.piece = null
  }

  override fun updateCaches(
    boardArray: Array<Array<Tile>>,
    cache: Map<String, MutableSet<Pair<Int, Int>>>,
  ) {
    val oldPositions = cache[boardArray[to.first][to.second].piece?.toString() ?: ""]
    checkNotNull(oldPositions) {
      "At this point the cache must have been found for piece " +
        boardArray[to.first][to.second].piece
    }
    oldPositions.remove(from)
    oldPositions.add(to)
  }
}
