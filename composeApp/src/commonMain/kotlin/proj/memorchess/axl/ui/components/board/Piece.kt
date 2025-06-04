package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import memorchess.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.IPiece
import proj.memorchess.axl.core.engine.pieces.Pawn
import proj.memorchess.axl.core.engine.pieces.vectors.*

@Composable
fun Piece(piece: IPiece) {
  Image(
    painter = painterResource(pieceToResource(piece)),
    contentDescription = stringResource(Res.string.description_board_piece, piece.toString()),
  )
}

private fun pieceToResource(piece: IPiece): DrawableResource {
  val resourceSuffix = if (piece.player == Game.Player.WHITE) "w" else "b"
  return drawableResource(
    "piece_${
            when (piece) {
                is proj.memorchess.axl.core.engine.pieces.vectors.King -> "king"
                is proj.memorchess.axl.core.engine.pieces.vectors.Knight -> "knight"
                is Bishop -> "bishop"
                is Queen -> "queen"
                is proj.memorchess.axl.core.engine.pieces.vectors.Rook -> "rook"
                is Pawn -> "pawn"
                else -> throw IllegalArgumentException("Unknown piece $piece.")
            }
        }_$resourceSuffix"
  )
}

@OptIn(ExperimentalResourceApi::class)
private fun drawableResource(name: String): DrawableResource {
  return Res.allDrawableResources[name]
    ?: throw IllegalArgumentException("Drawable resource $name not found.")
}
