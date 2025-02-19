package proj.ankichess.axl.core.game

import proj.ankichess.axl.core.game.board.Board
import proj.ankichess.axl.core.game.parser.FenParser
import proj.ankichess.axl.core.game.pieces.material.Pawn
import proj.ankichess.axl.core.game.pieces.moves.Castle
import proj.ankichess.axl.core.game.pieces.moves.IMove
import proj.ankichess.axl.core.game.pieces.moves.MoveFactory

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
  private val moveFactory = MoveFactory(board)

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

    return moveList
  }

  fun playMove(stringMove: String) {
    playMove(moveFactory.parseMove(stringMove, playerTurn))
  }

  fun playMove(move: IMove) {
    board.playMove(move)
    playerTurn = playerTurn.other()
  }

  override fun toString(): String {
    return FenParser.parse(this)
  }
}
