package proj.ankichess.axl.core.engine.moves

import kotlin.math.abs
import proj.ankichess.axl.core.engine.board.Board
import proj.ankichess.axl.core.engine.pieces.IPiece

class Castle(
  private val rook: Pair<Int, Int>,
  private val king: Pair<Int, Int>,
  private val rookDestination: Pair<Int, Int>,
  private val kingDestination: Pair<Int, Int>,
) : IMove {

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

  fun isPositionCorrect(board: Board): Boolean {
    return board.getTile(king).getSafePiece()?.toString()?.lowercase() == IPiece.KING &&
      board.getTile(rook).getSafePiece()?.toString()?.lowercase() == IPiece.ROOK
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
