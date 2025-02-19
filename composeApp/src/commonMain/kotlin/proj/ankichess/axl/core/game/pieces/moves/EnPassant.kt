package proj.ankichess.axl.core.game.pieces.moves

import proj.ankichess.axl.core.game.board.Tile

class EnPassant(from: Pair<Int, Int>, to: Pair<Int, Int>, private val captured: Pair<Int, Int>) :
  ClassicMove(from, to) {

  private var takenPiece: String? = null

  override fun play(boardArray: Array<Array<Tile>>) {
    super.play(boardArray)
    val capturedTile = boardArray[captured.first][captured.second]
    takenPiece = capturedTile.piece?.toString()
    capturedTile.piece = null
  }

  override fun updateCaches(
      boardArray: Array<Array<Tile>>,
      cache: Map<String, MutableSet<Pair<Int, Int>>>,
  ) {
    super.updateCaches(boardArray, cache)
    cache[takenPiece]?.remove(captured)
  }
}
