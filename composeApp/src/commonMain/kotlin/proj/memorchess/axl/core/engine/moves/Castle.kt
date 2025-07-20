package proj.memorchess.axl.core.engine.moves

import kotlin.math.abs
import proj.memorchess.axl.core.engine.board.IBoard
import proj.memorchess.axl.core.engine.pieces.vectors.King
import proj.memorchess.axl.core.engine.pieces.vectors.Rook

class Castle(
  private val rook: Pair<Int, Int>,
  private val king: Pair<Int, Int>,
  private val rookDestination: Pair<Int, Int>,
  private val kingDestination: Pair<Int, Int>,
) : Move {

  override fun destination(): Pair<Int, Int> {
    return rook
  }

  override fun origin(): Pair<Int, Int> {
    return king
  }

  override fun generateChanges(): Map<Pair<Int, Int>, Pair<Int, Int>?> {
    return linkedMapOf(rookDestination to rook, kingDestination to king, rook to null, king to null)
  }

  fun isLong(): Boolean {
    return abs(rook.first - king.first) == 4
  }

  fun isPositionCorrect(board: IBoard): Boolean {
    return board.getTile(king).getSafePiece() is King && board.getTile(rook).getSafePiece() is Rook
  }

  fun isPossible(board: IBoard): Boolean {
    var index = 1
    val direction = if (rook.second < king.second) 1 else -1
    while (index * direction + rook.second < king.second) {
      if (board.getTile(Pair(rook.first, index * direction + rook.second)).getSafePiece() != null) {
        return false
      }
      index++
    }
    return isPositionCorrect(board)
  }

  companion object {
    const val LONG_CASTLE_STRING = "O-O-O"
    const val SHORT_CASTLE_STRING = "O-O"

    /** Castles are immutable moves. Order: KQkq. */
    val castles =
      listOf(
        Castle(Pair(0, 7), Pair(0, 4), Pair(0, 5), Pair(0, 6)),
        Castle(Pair(0, 0), Pair(0, 4), Pair(0, 3), Pair(0, 2)),
        Castle(Pair(7, 7), Pair(7, 4), Pair(7, 5), Pair(7, 6)),
        Castle(Pair(7, 0), Pair(7, 4), Pair(7, 3), Pair(7, 2)),
      )
  }
}
