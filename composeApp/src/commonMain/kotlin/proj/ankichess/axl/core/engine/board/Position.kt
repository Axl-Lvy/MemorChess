package proj.ankichess.axl.core.engine.board

import proj.ankichess.axl.core.engine.Game
import proj.ankichess.axl.core.engine.parser.FenParser
import proj.ankichess.axl.core.engine.pieces.Pawn
import proj.ankichess.axl.core.intf.engine.board.IBoard
import proj.ankichess.axl.core.intf.engine.board.IPosition

class Position(
  override val board: IBoard,
  override var playerTurn: Game.Player,
  override val possibleCastles: Array<Boolean>,
  override var enPassantColumn: Int,
) : IPosition {

  constructor(board: IBoard) : this(board, Game.Player.WHITE, arrayOf(true, true, true, true), -1)

  constructor() : this(Board.createFromStartingPosition())

  override fun toString(): String {
    val fen = FenParser.parsePosition(this).split(" ")
    val keyBuilder = StringBuilder()
    for (i in 0..2) {
      keyBuilder.append(fen[i]).append(" ")
    }
    if (isEnpassantNecessary()) {
      keyBuilder.append(fen[2]).append(" ")
    }
    return keyBuilder.toString()
  }

  private fun isEnpassantNecessary(): Boolean {
    if (enPassantColumn == -1) {
      return false
    }

    val checkingRow = if (playerTurn == Game.Player.WHITE) 4 else 3
    if (enPassantColumn > 0) {
      val piece = board.getTile(checkingRow, enPassantColumn + 1).getSafePiece()
      if (piece is Pawn && piece.player == playerTurn.other()) {
        return true
      }
    }
    if (enPassantColumn < 7) {
      val piece = board.getTile(checkingRow, enPassantColumn - 1).getSafePiece()
      if (piece is Pawn && piece.player == playerTurn.other()) {
        return true
      }
    }
    return false
  }
}
