package proj.ankichess.axl.core.game.pieces.moves

import proj.ankichess.axl.core.game.board.Tile

class Capture(from: Pair<Int, Int>, to: Pair<Int, Int>) : ClassicMove(from, to) {

  private var takenPiece: String? = null

  override fun play(boardArray: Array<Array<Tile>>) {
    takenPiece = boardArray[to.first][to.second].piece.toString()
    super.play(boardArray)
  }

  override fun updateCaches(
    boardArray: Array<Array<Tile>>,
    cache: Map<String, MutableSet<Pair<Int, Int>>>,
  ) {
    super.updateCaches(boardArray, cache)
    cache[takenPiece]?.remove(to)
  }
}
