package proj.memorchess.axl.core.engine

import co.touchlab.kermit.Logger
import kotlin.math.abs
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.engine.board.IPosition
import proj.memorchess.axl.core.engine.board.Position
import proj.memorchess.axl.core.engine.moves.Castle
import proj.memorchess.axl.core.engine.moves.IllegalMoveException
import proj.memorchess.axl.core.engine.moves.Move
import proj.memorchess.axl.core.engine.moves.Promoter
import proj.memorchess.axl.core.engine.moves.description.MoveDescription
import proj.memorchess.axl.core.engine.moves.factory.CheckChecker
import proj.memorchess.axl.core.engine.moves.factory.DummyCheckChecker
import proj.memorchess.axl.core.engine.moves.factory.RealMoveFactory
import proj.memorchess.axl.core.engine.parser.FenParser
import proj.memorchess.axl.core.engine.pieces.Pawn

/**
 * Game instance.
 *
 * @constructor Creates a game from a given position.
 */
class Game(val position: IPosition, private val checkChecker: CheckChecker) {
  constructor(position: IPosition) : this(position, DummyCheckChecker(position))

  /** Creates a game from the starting position. */
  constructor() : this(Position())

  constructor(positionIdentifier: PositionIdentifier) : this(positionIdentifier.createPosition())

  /** Number of moves. */
  var moveCount = 1

  /** Number of half moves since the last pawn forward or the last capture. */
  var lastCaptureOrPawnHalfMove = 0

  private val promoter = Promoter(position.board)

  /** Move factory. */
  private val moveFactory = RealMoveFactory(position)

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
  fun availableMoves(x: Int, y: Int): Collection<Move> {
    val tile = position.board.getTile(x, y)
    if (tile.getSafePiece()?.player != position.playerTurn) {
      return emptyList()
    }

    val moveList = mutableListOf<Move>()

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
      if (position.playerTurn == Player.WHITE) newPieceName.uppercase()
      else newPieceName.lowercase()
    promoter.applyPromotion()
    position.playerTurn = position.playerTurn.other()
    if (position.playerTurn == Player.WHITE) {
      moveCount++
    }
  }

  /** True if this game needs a promotion to continue. */
  fun needPromotion(): Boolean {
    return promoter.needPromotion
  }

  private fun playMove(move: Move) {
    if (checkChecker.isPossible(move)) {
      LOGGER.i { "Playing ${moveFactory.stringifyMove(move)}." }
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

  private fun afterPlayMove(move: Move) {
    updatePossibleCastle()
    updateEnPassant(move)
    promoter.update(move)
    if (!promoter.needPromotion) {
      position.playerTurn = position.playerTurn.other()
      if (position.playerTurn == Player.WHITE) {
        moveCount++
      }
    }
  }

  private fun updatePossibleCastle() {
    for (i in 0..3) {
      position.possibleCastles[i] =
        position.possibleCastles[i] && Castle.castles[i].isPositionCorrect(position.board)
    }
  }

  private fun updateEnPassant(move: Move) {
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
}

private val LOGGER = Logger.withTag("Game")
