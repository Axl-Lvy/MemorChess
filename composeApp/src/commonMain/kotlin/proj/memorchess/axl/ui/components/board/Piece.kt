package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import memorchess.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.Pawn
import proj.memorchess.axl.core.engine.pieces.Piece
import proj.memorchess.axl.core.engine.pieces.vectors.*

@Composable
fun Piece(piece: Piece, modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(pieceToResource(piece)),
    contentDescription = stringResource(Res.string.description_board_piece, piece.toString()),
    contentScale = ContentScale.FillBounds,
    modifier = modifier,
  )
}

private fun pieceToResource(piece: Piece): DrawableResource {
  val resourceSuffix = if (piece.player == Game.Player.WHITE) "w" else "b"
  return drawableResource(
    "piece_${
            when (piece) {
                is King -> "king"
                is Knight -> "knight"
                is Bishop -> "bishop"
                is Queen -> "queen"
                is Rook -> "rook"
                is Pawn -> "pawn"
                else -> throw IllegalArgumentException("Unknown piece $piece.")
            }
        }_$resourceSuffix"
  )
}

private fun drawableResource(name: String): DrawableResource {
  return Res.allDrawableResources[name]
    ?: throw IllegalArgumentException("Drawable resource $name not found.")
}
