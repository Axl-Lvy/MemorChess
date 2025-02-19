package proj.ankichess.axl.core.game.pieces.moves

import proj.ankichess.axl.core.game.board.Tile
import kotlin.math.abs

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

  override fun play(boardArray: Array<Array<Tile>>) {
    val rookTile = boardArray[rook.first][rook.second]
    val rookDestinationTile = boardArray[rookDestination.first][rookDestination.second]
    val kingTile = boardArray[king.first][king.second]
    val kingDestinationTile = boardArray[kingDestination.first][kingDestination.second]

    rookDestinationTile.piece = rookTile.piece
    kingDestinationTile.piece = kingTile.piece
    rookTile.piece = null
    kingTile.piece = null
  }

  override fun updateCaches(
      boardArray: Array<Array<Tile>>,
      cache: Map<String, MutableSet<Pair<Int, Int>>>,
  ) {
    val rookCache =
      cache[boardArray[rookDestination.first][rookDestination.second].piece?.toString()]
    checkNotNull(rookCache)
    rookCache.add(rookDestination)
    rookCache.remove(rook)

    val kingCache =
      cache[boardArray[kingDestination.first][kingDestination.second].piece?.toString()]
    checkNotNull(kingCache)
    kingCache.add(kingDestination)
    kingCache.remove(king)
  }

  fun isLong(): Boolean {
    return abs(rook.first - king.first) == 4
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
