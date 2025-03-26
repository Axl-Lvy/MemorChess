package proj.ankichess.axl.core.engine

import com.diamondedge.logging.logging
import kotlin.math.abs
import proj.ankichess.axl.core.engine.board.Board
import proj.ankichess.axl.core.engine.moves.Castle
import proj.ankichess.axl.core.engine.moves.IMove
import proj.ankichess.axl.core.engine.moves.IllegalMoveException
import proj.ankichess.axl.core.engine.moves.Promoter
import proj.ankichess.axl.core.engine.moves.description.MoveDescription
import proj.ankichess.axl.core.engine.moves.factory.ACheckChecker
import proj.ankichess.axl.core.engine.moves.factory.SimpleMoveFactory
import proj.ankichess.axl.core.engine.parser.FenParser
import proj.ankichess.axl.core.engine.pieces.Pawn

/**
 * Game instance.
 *
 * @property board Starting position.
 * @constructor Creates a game from a given position.
 */
class Game(val board: Board, private val checkChecker: ACheckChecker) {
  constructor(
    board: Board
  ) : this(board, proj.ankichess.axl.core.engine.moves.factory.DummyCheckChecker(board))

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

  val promoter = Promoter(board)

  /** Move factory. */
  private val moveFactory = SimpleMoveFactory(board)

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

    moveList.addAll(
      moveFactory.extractMoves(
        tile.getSafePiece()?.availableMoves(Pair(x, y)) ?: emptyList(),
        enPassantColumn,
      )
    )

    for (i in 0..3) {
      if (possibleCastle[i]) {
        moveList.add(Castle.castles[i])
      }
    }

    return moveList.filter { checkChecker.isPossible(it, enPassantColumn) }
  }

  /**
   * Plays a move.
   *
   * @param moveDescription The description of the move.
   */
  fun playMove(moveDescription: MoveDescription) {
    val immutableOriginPiece = board.getTile(moveDescription.from).getSafePiece()
    if (
      immutableOriginPiece != null &&
        immutableOriginPiece.player == playerTurn &&
        immutableOriginPiece.isMovePossible(moveDescription)
    ) {
      val move = moveFactory.createMoveFrom(moveDescription, enPassantColumn)
      if (move == null) {
        throw IllegalMoveException("$moveDescription is invalid.")
      } else {
        playMove(move)
      }
    } else {
      throw IllegalMoveException("Cannot play $moveDescription.")
    }
  }

  /**
   * Plays a move.
   *
   * @param stringMove The name of the move.
   */
  fun playMove(stringMove: String) {
    promoter.savePromotion(stringMove)
    playMove(moveFactory.parseMove(stringMove, playerTurn, enPassantColumn, checkChecker))
  }

  private fun playMove(move: IMove) {
    if (checkChecker.isPossible(move, enPassantColumn)) {
      LOGGER.info { "Playing ${moveFactory.stringifyMove(move)}." }
      beforePlayMove()
      board.playMove(move)
      afterPlayMove(move)
    } else {
      throw IllegalMoveException("Move ${moveFactory.stringifyMove(move)} is blocked by a check.")
    }
  }

  private fun beforePlayMove() {
    if (promoter.needPromotion) {
      throw IllegalMoveException("Need a promotion.")
    }
  }

  private fun afterPlayMove(move: IMove) {
    updatePossibleCastle()
    updateEnPassant(move)
    promoter.update(move)
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

  companion object {
    val LOGGER = logging()
  }
}
