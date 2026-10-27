package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.description_board_piece
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.engine.ChessPiece

/**
 * Renders a single chess piece from the preloaded [LocalPiecePainters].
 *
 * @param contentDescription Accessibility description; describes the piece when `null`.
 */
@Composable
fun Piece(piece: ChessPiece, modifier: Modifier = Modifier, contentDescription: String? = null) {
  val painter =
    LocalPiecePainters.current[piece]
      ?: error("No painter cached for $piece — PiecePaintersProvider not in scope")
  Image(
    painter = painter,
    contentDescription =
      contentDescription ?: stringResource(Res.string.description_board_piece, piece.toString()),
    contentScale = ContentScale.Fit,
    modifier = modifier,
  )
}
