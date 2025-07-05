package proj.memorchess.axl.core.engine.pieces

import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.vectors.*

object PieceFactory {
  fun createPiece(stringPiece: String): IPiece {
    val player =
      if (stringPiece.uppercase() == stringPiece) Game.Player.WHITE else Game.Player.BLACK
    return when (stringPiece.lowercase()) {
      IPiece.ROOK -> Rook(player)
      IPiece.QUEEN -> Queen(player)
      IPiece.KING -> King(player)
      IPiece.BISHOP -> Bishop(player)
      IPiece.KNIGHT -> Knight(player)
      IPiece.PAWN -> Pawn(player)
      else -> throw IllegalArgumentException("Invalid piece: $stringPiece")
    }
  }
}
