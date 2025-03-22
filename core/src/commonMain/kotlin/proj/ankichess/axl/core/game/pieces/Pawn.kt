package proj.ankichess.axl.core.game.pieces

import kotlin.math.abs
import proj.ankichess.axl.core.game.Game
import proj.ankichess.axl.core.game.moves.description.MoveDescription

/** Pawn. */
class Pawn(player: Game.Player) : APiece(player) {

  override fun availableMoves(coords: Pair<Int, Int>): List<List<MoveDescription>> {
    val moveList = mutableListOf<List<MoveDescription>>()
    val frontMoves = mutableListOf<MoveDescription>()
    val forward = if (player == Game.Player.WHITE) 1 else -1
    frontMoves.add(MoveDescription(coords, Pair(coords.first, coords.second + forward)))
    if (player == Game.Player.WHITE && coords.second == 1) {
      frontMoves.add(MoveDescription(coords, Pair(coords.first, coords.second + 2)))
    } else if (player == Game.Player.BLACK && coords.second == 6) {
      frontMoves.add(MoveDescription(coords, Pair(coords.first, coords.second - 2)))
    }
    moveList.add(frontMoves)

    if (coords.first > 0) {
      moveList.add(listOf(MoveDescription(coords, Pair(coords.first - 1, coords.second + forward))))
    }
    if (coords.first < 7) {
      moveList.add(listOf(MoveDescription(coords, Pair(coords.first + 1, coords.second + forward))))
    }

    return moveList
  }

  override fun isMovePossible(move: MoveDescription): Boolean {
    val forward = if (player == Game.Player.WHITE) 1 else -1
    return if (move.to.second != move.from.second) {
      move.to.first == (move.from.first + forward) && abs(move.to.second - move.from.second) == 1
    } else {
      if ((move.from.first == 1 && forward == 1) || (move.from.first == 6 && forward == -1)) {
        ((move.to.first - move.from.first) == (2 * forward)) ||
          ((move.to.first - move.from.first) == forward)
      } else {
        (move.to.first - move.from.first) == forward
      }
    }
  }

  override fun baseChar(): String {
    return IPiece.PAWN
  }
}
