package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import memorchess.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.core.engine.Player

@Composable
fun Piece(piece: ChessPiece, modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(pieceToResource(piece)),
    contentDescription = stringResource(Res.string.description_board_piece, piece.toString()),
    contentScale = ContentScale.Fit,
    modifier = modifier,
  )
}

private fun pieceToResource(piece: ChessPiece): DrawableResource {
  val resourceSuffix = if (piece.player == Player.WHITE) "w" else "b"
  return drawableResource(
    "piece_${
            when (piece.kind) {
                PieceKind.KING -> "king"
                PieceKind.KNIGHT -> "knight"
                PieceKind.BISHOP -> "bishop"
                PieceKind.QUEEN -> "queen"
                PieceKind.ROOK -> "rook"
                PieceKind.PAWN -> "pawn"
            }
        }_$resourceSuffix"
  )
}

private fun drawableResource(name: String): DrawableResource {
  return Res.allDrawableResources[name]
    ?: throw IllegalArgumentException("Drawable resource $name not found.")
}
