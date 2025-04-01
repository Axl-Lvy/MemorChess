package proj.ankichess.axl.core.intf.engine.board

import proj.ankichess.axl.core.intf.engine.moves.IMove
import proj.ankichess.axl.core.intf.engine.pieces.IPiece

/** Chess board. */
interface IBoard {

  companion object {

    fun getColumnNumber(columnName: String): Int {
      return when (columnName) {
        "a" -> 0
        "b" -> 1
        "c" -> 2
        "d" -> 3
        "e" -> 4
        "f" -> 5
        "g" -> 6
        "h" -> 7
        else -> throw IllegalArgumentException("Invalid column name: $columnName.")
      }
    }

    fun getColumnName(columnNumber: Int): String {
      return when (columnNumber) {
        0 -> "a"
        1 -> "b"
        2 -> "c"
        3 -> "d"
        4 -> "e"
        5 -> "f"
        6 -> "g"
        7 -> "h"
        else -> throw IllegalArgumentException("Invalid column number: $columnNumber.")
      }
    }

    fun getTileName(coords: Pair<Int, Int>): String {
      return getColumnName(coords.second) + (coords.first + 1)
    }

    fun getCoords(tileName: String): Pair<Int, Int> {
      require(tileName.length == 2) { "$tileName is not a valid tile name." }
      return Pair(tileName[1].toString().toInt() - 1, getColumnNumber(tileName[0].toString()))
    }
  }

  /** A cache to remember the position of each piece. */
  val piecePositionsCache: Map<String, MutableSet<Pair<Int, Int>>>

  /** Resets the board. */
  fun reset()

  /** Sets the board to the starting position. */
  fun startingPosition()

  /**
   * Gets a tile by its name.
   *
   * @param tileName The name of the tile (e.g., "a1").
   * @return The tile at the specified name.
   */
  fun getTile(tileName: String): ITile

  /**
   * Gets a tile by its coordinates.
   *
   * @param coords The coordinates of the tile (e.g., (0, 0) for "a1").
   * @return The tile at the specified coordinates.
   */
  fun getTile(coords: Pair<Int, Int>): ITile

  /**
   * Gets a tile by its coordinates.
   *
   * @param x The x-coordinate of the tile.
   * @param y The y-coordinate of the tile.
   * @return The tile at the specified coordinates.
   */
  fun getTile(x: Int, y: Int): ITile

  /**
   * Gets every tile on the board, from a1 to h8.
   *
   * @return An iterator over all tiles on the board.
   */
  fun getTilesIterator(): Iterator<ITile>

  /**
   * Places a piece on the board.
   *
   * @param tileName The tile name (e.g., "a1").
   * @param p The piece to place (e.g., "K" for white king).
   */
  fun placePiece(tileName: String, p: String)

  /**
   * Places a piece on the board.
   *
   * @param x The x-coordinate of the tile.
   * @param y The y-coordinate of the tile.
   * @param p The piece to place (e.g., "K" for white king).
   */
  fun placePiece(x: Int, y: Int, p: String)

  /**
   * Places a piece on the board.
   *
   * @param coords The coordinates of the tile (e.g., (0, 0) for "a1").
   * @param p The piece to place.
   */
  fun placePiece(coords: Pair<Int, Int>, p: IPiece)

  /**
   * Plays a move and updates the board.
   *
   * @param move The move to play.
   */
  fun playMove(move: IMove)
}
