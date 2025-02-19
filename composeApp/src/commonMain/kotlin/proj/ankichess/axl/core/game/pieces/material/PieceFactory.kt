package proj.ankichess.axl.core.game.pieces.material

import proj.ankichess.axl.core.game.Game

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
