package proj.ankichess.axl.ui.components.board

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import ankichess.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.impl.engine.pieces.Pawn
import proj.ankichess.axl.core.impl.engine.pieces.vectors.*
import proj.ankichess.axl.core.intf.engine.pieces.IPiece

@Composable
fun Piece(piece: IPiece) {
  Image(painter = painterResource(pieceToResource(piece)), contentDescription = piece.toString())
}

fun pieceToResource(piece: IPiece): DrawableResource {
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

@OptIn(ExperimentalResourceApi::class)
private fun drawableResource(name: String): DrawableResource {
  return Res.allDrawableResources[name]
    ?: throw IllegalArgumentException("Drawable resource $name not found.")
}
