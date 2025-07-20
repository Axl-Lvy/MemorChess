package proj.memorchess.axl.core.engine.board

import proj.memorchess.axl.core.engine.Game.Player
import proj.memorchess.axl.core.engine.board.IBoard.Companion.getCoords
import proj.memorchess.axl.core.engine.moves.Move
import proj.memorchess.axl.core.engine.pieces.Pawn
import proj.memorchess.axl.core.engine.pieces.Piece
import proj.memorchess.axl.core.engine.pieces.PieceFactory
import proj.memorchess.axl.core.engine.pieces.vectors.Bishop
import proj.memorchess.axl.core.engine.pieces.vectors.King
import proj.memorchess.axl.core.engine.pieces.vectors.Knight
import proj.memorchess.axl.core.engine.pieces.vectors.Queen
import proj.memorchess.axl.core.engine.pieces.vectors.Rook

/** Main implementation of [IBoard]. */
class Board(
  override val piecePositionsCache: Map<String, MutableSet<Pair<Int, Int>>> =
    Piece.PIECES.flatMap { listOf(it, it.uppercase()) }.associateWith { mutableSetOf() },
  private val array: Array<Array<Tile>> = createEmptyArray(),
) : IBoard {

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
    private fun createEmptyArray(): Array<Array<Tile>> {
      return Array(8) { row -> Array(8) { col -> Tile(row, col) } }
    }

    /**
     * Creates a new board from starting position.
     *
     * @return The board.
     */
    fun createFromStartingPosition(): IBoard {
      val board = Board()
      board.startingPosition()
      return board
    }
  }

  override fun reset() {
    for (tiles in array) {
      for (tile in tiles) {
        tile.reset()
      }
    }
    piecePositionsCache.values.forEach { i -> i.clear() }
  }

  override fun startingPosition() {
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

  override fun getTile(tileName: String): ITile {
    return getTile(getCoords(tileName))
  }

  override fun getTile(coords: Pair<Int, Int>): ITile {
    return getTile(coords.first, coords.second)
  }

  override fun getTile(x: Int, y: Int): ITile {
    return array[x][y]
  }

  override fun getTilesIterator(): Iterator<ITile> {
    return array.flatten().iterator()
  }

  override fun placePiece(tileName: String, p: String) {
    placePiece(getCoords(tileName), PieceFactory.createPiece(p))
  }

  override fun placePiece(x: Int, y: Int, p: String) {
    placePiece(Pair(x, y), PieceFactory.createPiece(p))
  }

  override fun placePiece(coords: Pair<Int, Int>, p: Piece) {
    val tile = array[coords.first][coords.second]
    tile.piece = p
    addPositionToCache(tile)
  }

  override fun playMove(move: Move) {
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

  override fun copy(): IBoard {
    val newBoard = Board(piecePositionsCache.toMap())
    for (i in 0..7) {
      for (j in 0..7) {
        val tile = array[i][j]
        newBoard.array[i][j].piece = tile.piece
        if (tile.piece != null) {
          newBoard.addPositionToCache(newBoard.array[i][j])
        }
      }
    }
    return newBoard
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Board) return false

    if (!array.contentDeepEquals(other.array)) return false

    return true
  }

  override fun hashCode(): Int {
    return array.contentDeepHashCode()
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
