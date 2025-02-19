package proj.ankichess.axl.core.game.board

import proj.ankichess.axl.core.game.Game.Player
import proj.ankichess.axl.core.game.moves.IMove
import proj.ankichess.axl.core.game.pieces.IPiece
import proj.ankichess.axl.core.game.pieces.Pawn
import proj.ankichess.axl.core.game.pieces.PieceFactory
import proj.ankichess.axl.core.game.pieces.vectors.Bishop
import proj.ankichess.axl.core.game.pieces.vectors.King
import proj.ankichess.axl.core.game.pieces.vectors.Knight
import proj.ankichess.axl.core.game.pieces.vectors.Queen
import proj.ankichess.axl.core.game.pieces.vectors.Rook

/**
 * Board game.
 *
 * @constructor Creates empty Board.
 */
class Board {

  val piecePositionsCache: Map<String, MutableSet<Pair<Int, Int>>> =
    IPiece.PIECES.flatMap { listOf(it, it.uppercase()) }.associateWith { mutableSetOf() }

  /** Array of tiles representing the chess board. */
  private val array = createEmptyArray()

  /**
   * Factory.
   *
   * @constructor Creates empty Factory.
   */
  companion object {

    /**
     * Creates empty array.
     *
     * @return An empty array.
     */
    fun createEmptyArray(): Array<Array<Tile>> {
      return Array(8) { row -> Array(8) { col -> Tile(row, col) } }
    }

    /**
     * Creates a new board from starting position.
     *
     * @return The board.
     */
    fun createFromStartingPosition(): Board {
      val board = Board()
      board.startingPosition()
      return board
    }

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

  /** Resets the board. */
  fun reset() {
    for (tiles in array) {
      for (tile in tiles) {
        tile.reset()
      }
    }
    piecePositionsCache.values.forEach { i -> i.clear() }
  }

  /** Sets the board to the starting position. */
  fun startingPosition() {
    reset()
    for (col in 0..7) {
      array[1][col].piece = Pawn(Player.WHITE)
      addPositionToCache(array[1][col])
      array[6][col].piece = Pawn(Player.BLACK)
      addPositionToCache(array[6][col])
    }
    for (row in listOf(0, 7)) {
      val color = if (row == 0) Player.WHITE else Player.BLACK
      array[row][0].piece = Rook(color)
      addPositionToCache(array[row][0])
      array[row][1].piece = Knight(color)
      addPositionToCache(array[row][1])
      array[row][2].piece = Bishop(color)
      addPositionToCache(array[row][2])
      array[row][3].piece = Queen(color)
      addPositionToCache(array[row][3])
      array[row][4].piece = King(color)
      addPositionToCache(array[row][4])
      array[row][5].piece = Bishop(color)
      addPositionToCache(array[row][5])
      array[row][6].piece = Knight(color)
      addPositionToCache(array[row][6])
      array[row][7].piece = Rook(color)
      addPositionToCache(array[row][7])
    }
  }

  private fun addPositionToCache(tile: Tile) {
    piecePositionsCache[tile.piece.toString()]?.add(tile.getCoords())
  }

  fun getTile(tileName: String): ITile {
    return getTile(getCoords(tileName))
  }

  fun getTile(coords: Pair<Int, Int>): ITile {
    return getTile(coords.first, coords.second)
  }

  fun getTile(x: Int, y: Int): ITile {
    return array[x][y]
  }

  fun getTilesIterator(): Iterator<ITile> {
    return array.flatten().iterator()
  }

  fun placePiece(t: String, p: String) {
    placePiece(getCoords(t), PieceFactory.createPiece(p))
  }

  fun placePiece(x: Int, y: Int, p: String) {
    placePiece(Pair(x, y), PieceFactory.createPiece(p))
  }

  fun placePiece(coords: Pair<Int, Int>, p: IPiece) {
    val tile = array[coords.first][coords.second]
    tile.piece = p
    addPositionToCache(tile)
  }

  fun playMove(move: IMove) {
    move.generateChanges().forEach { (changingTileCoords, ref) ->
      run {
        val changingTile = array[changingTileCoords.first][changingTileCoords.second]
        piecePositionsCache[changingTile.piece.toString()]?.remove(changingTile.getCoords())
        if (ref != null) {
          val refPiece = array[ref.first][ref.second].piece
          changingTile.piece = refPiece
          addPositionToCache(changingTile)
        } else {
          changingTile.reset()
        }
      }
    }
  }

  override fun toString(): String {
    val result = StringBuilder()
    val lineSeparator = "-----------------"
    result.append(lineSeparator)
    for (line in array.reversed()) {
      result.append("\n|")
      for (tile in line) {
        result.append(tile.toString()).append("|")
      }
      result.append("\n").append(lineSeparator)
    }
    return result.toString()
  }
}
