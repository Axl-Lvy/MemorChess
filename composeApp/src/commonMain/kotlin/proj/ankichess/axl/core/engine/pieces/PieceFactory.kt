package proj.ankichess.axl.core.engine.pieces

import proj.ankichess.axl.core.engine.Game
import proj.ankichess.axl.core.engine.pieces.vectors.*

object PieceFactory {
  fun createPiece(stringPiece: String): IPiece {
    val player =
      if (stringPiece.uppercase() == stringPiece) Game.Player.WHITE else Game.Player.BLACK
    return when (stringPiece.lowercase()) {
      IPiece.ROOK -> proj.ankichess.axl.core.engine.pieces.vectors.Rook(player)
      IPiece.QUEEN -> Queen(player)
      IPiece.KING -> proj.ankichess.axl.core.engine.pieces.vectors.King(player)
      IPiece.BISHOP -> Bishop(player)
      IPiece.KNIGHT -> proj.ankichess.axl.core.engine.pieces.vectors.Knight(player)
      IPiece.PAWN -> Pawn(player)
      else -> throw IllegalArgumentException("Invalid piece: $stringPiece")
    }
  }
}
