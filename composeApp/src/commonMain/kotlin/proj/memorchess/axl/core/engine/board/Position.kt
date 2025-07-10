package proj.memorchess.axl.core.engine.board

import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.parser.FenParser
import proj.memorchess.axl.core.engine.pieces.Pawn

/** Main implementation of [IPosition] */
class Position(
  override val board: IBoard,
  override var playerTurn: Game.Player,
  override val possibleCastles: Array<Boolean>,
  override var enPassantColumn: Int,
) : IPosition {

  constructor(board: IBoard) : this(board, Game.Player.WHITE, arrayOf(true, true, true, true), -1)

  constructor() : this(Board.createFromStartingPosition())

  private fun isEnpassantNecessary(): Boolean {
    if (enPassantColumn == -1) {
      return false
    }

    val checkingRow = if (playerTurn == Game.Player.WHITE) 4 else 3
    if (enPassantColumn > 0) {
      val piece = board.getTile(checkingRow, enPassantColumn - 1).getSafePiece()
      if (piece is Pawn && piece.player == playerTurn.other()) {
        return true
      }
    }
    if (enPassantColumn < 7) {
      val piece = board.getTile(checkingRow, enPassantColumn + 1).getSafePiece()
      if (piece is Pawn && piece.player == playerTurn.other()) {
        return true
      }
    }
    return false
  }

  override fun toString(): String {
    val fen = FenParser.parsePosition(this).split(" ")
    val keyBuilder = StringBuilder()
    for (i in 0..2) {
      keyBuilder.append(fen[i]).append(" ")
    }
    if (isEnpassantNecessary()) {
      keyBuilder.append(fen[2])
    }
    return keyBuilder.toString().trim()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Position) return false

    if (board != other.board) return false
    if (playerTurn != other.playerTurn) return false
    if (!possibleCastles.contentEquals(other.possibleCastles)) return false
    if (enPassantColumn != other.enPassantColumn) return false
    return true
  }

  override fun hashCode(): Int {
    var result = board.hashCode()
    result = 31 * result + playerTurn.hashCode()
    result = 31 * result + possibleCastles.contentHashCode()
    result = 31 * result + enPassantColumn
    return result
  }

  override fun createIdentifier(): PositionIdentifier {
    return PositionIdentifier(FenParser.parsePosition(this, isEnpassantNecessary()))
  }
}
