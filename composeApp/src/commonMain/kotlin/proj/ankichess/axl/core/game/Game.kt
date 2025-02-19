package proj.ankichess.axl.core.game

import kotlin.math.abs
import proj.ankichess.axl.core.game.board.Board
import proj.ankichess.axl.core.game.moves.Castle
import proj.ankichess.axl.core.game.moves.IMove
import proj.ankichess.axl.core.game.moves.factory.DummyCheckChecker
import proj.ankichess.axl.core.game.moves.factory.SimpleMoveFactory
import proj.ankichess.axl.core.game.parser.FenParser
import proj.ankichess.axl.core.game.pieces.Pawn

/**
 * Game instance.
 *
 * @property board Starting position.
 * @constructor Creates a game from a given position.
 */
class Game(val board: Board) {

  /** Player turn. */
  var playerTurn = Player.WHITE

  /** Castle moves that are still allowed (KQkq) */
  val possibleCastle = Array(4) { true }

  /** En passant column. -1 for no one. */
  var enPassantColumn = -1

  /** Number of moves. */
  var moveCount = 1

  /** Number of half moves since the last pawn forward or the last capture. */
  var lastCaptureOrPawnHalfMove = 0

  /** Move factory. */
  private val moveFactory = SimpleMoveFactory(board)

  private val checkChecker = DummyCheckChecker(board)

  /** Creates a game from the starting position. */
  constructor() : this(Board.createFromStartingPosition())

  /** White and black player. */
  enum class Player {
    WHITE,
    BLACK;

    fun other(): Player = if (this == WHITE) BLACK else WHITE
  }

  /**
   * Available moves for a tiles.
   *
   * @param x Line.
   * @param y Column.
   * @return Possible moves.
   */
  fun availableMoves(x: Int, y: Int): Collection<IMove> {
    val tile = board.getTile(x, y)
    if (tile.getSafePiece()?.player != playerTurn) {
      return emptyList()
    }

    val moveList = mutableListOf<IMove>()

    if (tile.getSafePiece() is Pawn) {
      moveList.addAll(
        moveFactory.extractPawnMoves(
          tile.getSafePiece()?.availableMoves(Pair(x, y)) ?: emptyList(),
          enPassantColumn,
        )
      )
    } else {
      moveList.addAll(
        moveFactory.extractMoves(tile.getSafePiece()?.availableMoves(Pair(x, y)) ?: emptyList())
      )
    }

    for (i in 0..3) {
      if (possibleCastle[i]) {
        moveList.add(Castle.castles[i])
      }
    }

    return moveList.filter { checkChecker.isPossible(it) }
  }

  fun safePlayMove(stringMove: String) {
    safePlayMove(moveFactory.parseMove(stringMove, playerTurn, enPassantColumn))
  }

  fun safePlayMove(move: IMove) {
    if (checkChecker.isPossible(move)) {
      playMove(move)
    }
  }

  fun playMove(stringMove: String) {
    playMove(moveFactory.parseMove(stringMove, playerTurn, enPassantColumn))
  }

  private fun playMove(move: IMove) {
    board.playMove(move)
    aftenPlayMove(move)
  }

  private fun aftenPlayMove(move: IMove) {
    updatePossibleCastle()
    updateEnPassant(move)
    playerTurn = playerTurn.other()
    if (playerTurn == Player.WHITE) {
      moveCount++
    }
  }

  private fun updatePossibleCastle() {
    for (i in 0..3) {
      possibleCastle[i] = possibleCastle[i] && Castle.castles[i].isPositionCorrect(board)
    }
  }

  private fun updateEnPassant(move: IMove) {
    enPassantColumn =
      if (
        board.getTile(move.destination()).getSafePiece() is Pawn &&
          abs(move.origin().first - move.destination().first) == 2
      ) {
        move.destination().second
      } else {
        -1
      }
  }

  override fun toString(): String {
    return FenParser.parse(this)
  }
}
