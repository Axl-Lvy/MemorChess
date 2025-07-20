package proj.memorchess.axl.core.engine.pieces

import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.vectors.*

object PieceFactory {
  fun createPiece(stringPiece: String): Piece {
    val player =
      if (stringPiece.uppercase() == stringPiece) Game.Player.WHITE else Game.Player.BLACK
    return when (stringPiece.lowercase()) {
      Piece.ROOK -> Rook(player)
      Piece.QUEEN -> Queen(player)
      Piece.KING -> King(player)
      Piece.BISHOP -> Bishop(player)
      Piece.KNIGHT -> Knight(player)
      Piece.PAWN -> Pawn(player)
      else -> throw IllegalArgumentException("Invalid piece: $stringPiece")
    }
  }
}
