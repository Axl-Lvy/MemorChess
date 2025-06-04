package proj.ankichess.axl.core.engine

import com.diamondedge.logging.logging
import kotlin.math.abs
import proj.ankichess.axl.core.data.PositionKey
import proj.ankichess.axl.core.engine.board.IPosition
import proj.ankichess.axl.core.engine.board.Position
import proj.ankichess.axl.core.engine.moves.Castle
import proj.ankichess.axl.core.engine.moves.IMove
import proj.ankichess.axl.core.engine.moves.IllegalMoveException
import proj.ankichess.axl.core.engine.moves.Promoter
import proj.ankichess.axl.core.engine.moves.description.MoveDescription
import proj.ankichess.axl.core.engine.moves.factory.ACheckChecker
import proj.ankichess.axl.core.engine.moves.factory.DummyCheckChecker
import proj.ankichess.axl.core.engine.moves.factory.SimpleMoveFactory
import proj.ankichess.axl.core.engine.parser.FenParser
import proj.ankichess.axl.core.engine.pieces.Pawn

/**
 * Game instance.
 *
 * @constructor Creates a game from a given position.
 */
class Game(val position: IPosition, private val checkChecker: ACheckChecker) {
  constructor(position: IPosition) : this(position, DummyCheckChecker(position))

  /** Creates a game from the starting position. */
  constructor() : this(Position())

  constructor(positionKey: PositionKey) : this(positionKey.createPosition())

  /** Number of moves. */
  var moveCount = 1

  /** Number of half moves since the last pawn forward or the last capture. */
  var lastCaptureOrPawnHalfMove = 0

  private val promoter = Promoter(position.board)

  /** Move factory. */
  private val moveFactory = SimpleMoveFactory(position)

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
    val tile = position.board.getTile(x, y)
    if (tile.getSafePiece()?.player != position.playerTurn) {
      return emptyList()
    }

    val moveList = mutableListOf<IMove>()

    moveList.addAll(
      moveFactory.extractMoves(tile.getSafePiece()?.availableMoves(Pair(x, y)) ?: emptyList())
    )

    for (i in 0..3) {
      if (position.possibleCastles[i]) {
        moveList.add(Castle.castles[i])
      }
    }

    return moveList.filter { checkChecker.isPossible(it) }
  }

  /**
   * Plays a move.
   *
   * @param moveDescription The description of the move.
   */
  fun playMove(moveDescription: MoveDescription): String {
    val immutableOriginPiece = position.board.getTile(moveDescription.from).getSafePiece()
    if (
      immutableOriginPiece != null &&
        immutableOriginPiece.player == position.playerTurn &&
        immutableOriginPiece.isMovePossible(moveDescription)
    ) {
      val move = moveFactory.createMoveFrom(moveDescription)
      if (move == null) {
        throw IllegalMoveException("$moveDescription is invalid.")
      } else {
        val moveName = moveFactory.stringifyMove(move)
        playMove(move)
        return moveName
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
    playMove(moveFactory.parseMove(stringMove, checkChecker))
  }

  /**
   * Applies promotion.
   *
   * Because the promotion needs additional information, this function has to be called after the
   * move.
   *
   * @param newPieceName
   */
  fun applyPromotion(newPieceName: String) {
    check(promoter.needPromotion) { "No promotion to apply." }
    promoter.newPieceName =
      if (position.playerTurn == Player.WHITE) newPieceName.lowercase()
      else newPieceName.uppercase()
    promoter.applyPromotion()
  }

  private fun playMove(move: IMove) {
    if (checkChecker.isPossible(move)) {
      LOGGER.info { "Playing ${moveFactory.stringifyMove(move)}." }
      beforePlayMove()
      position.board.playMove(move)
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
    position.playerTurn = position.playerTurn.other()
    if (position.playerTurn == Player.WHITE) {
      moveCount++
    }
  }

  private fun updatePossibleCastle() {
    for (i in 0..3) {
      position.possibleCastles[i] =
        position.possibleCastles[i] && Castle.castles[i].isPositionCorrect(position.board)
    }
  }

  private fun updateEnPassant(move: IMove) {
    position.enPassantColumn =
      if (
        position.board.getTile(move.destination()).getSafePiece() is Pawn &&
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
