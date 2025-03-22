package proj.ankichess.axl.board

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import ankichess.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import proj.ankichess.axl.core.game.Game
import proj.ankichess.axl.core.game.pieces.IPiece

@Composable
fun Piece(piece: IPiece) {
  Image(painter = painterResource(pieceToResource(piece)), contentDescription = piece.toString())
}

fun pieceToResource(piece: IPiece): DrawableResource {
  return if (piece.player == Game.Player.WHITE) {
    when (piece.toString().lowercase()) {
      IPiece.BISHOP -> Res.drawable.piece_bishop_w
      IPiece.KNIGHT -> Res.drawable.piece_knight_w
      IPiece.ROOK -> Res.drawable.piece_rook_w
      IPiece.QUEEN -> Res.drawable.piece_queen_w
      IPiece.KING -> Res.drawable.piece_king_w
      IPiece.PAWN -> Res.drawable.piece_pawn_w
      else -> throw IllegalArgumentException("Unknown piece $piece.")
    }
  } else {
    when (piece.toString().lowercase()) {
      IPiece.BISHOP -> Res.drawable.piece_bishop_b
      IPiece.KNIGHT -> Res.drawable.piece_knight_b
      IPiece.ROOK -> Res.drawable.piece_rook_b
      IPiece.QUEEN -> Res.drawable.piece_queen_b
      IPiece.KING -> Res.drawable.piece_king_b
      IPiece.PAWN -> Res.drawable.piece_pawn_b
      else -> throw IllegalArgumentException("Unknown piece $piece.")
    }
  }
}
