package proj.memorchess.axl.core.engine.pieces

import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.moves.description.MoveDescription

/** A piece represents a real chess piece. */
interface Piece {

  companion object {
    const val ROOK = "r"
    const val KNIGHT = "n"
    const val BISHOP = "b"
    const val QUEEN = "q"
    const val KING = "k"
    const val PAWN = "p"

    val PIECES = listOf(ROOK, KNIGHT, BISHOP, QUEEN, KING, PAWN)
  }

  /** The player this piece belongs to. */
  val player: Game.Player

  /**
   * Creates available moves from this piece point of view. This means moves can be blocked by
   * another piece on the board.
   *
   * @param coords Coordinates of this piece.
   * @return Available moves. In a list of moves, if one is blocked then all next ones are also
   *   blocked.
   */
  fun availableMoves(coords: Pair<Int, Int>): List<List<MoveDescription>>

  fun isMovePossible(move: MoveDescription): Boolean
}
