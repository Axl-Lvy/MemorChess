package proj.memorchess.axl.ui.components.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.piece_bishop_b
import memorchess.composeapp.generated.resources.piece_bishop_w
import memorchess.composeapp.generated.resources.piece_king_b
import memorchess.composeapp.generated.resources.piece_king_w
import memorchess.composeapp.generated.resources.piece_knight_b
import memorchess.composeapp.generated.resources.piece_knight_w
import memorchess.composeapp.generated.resources.piece_pawn_b
import memorchess.composeapp.generated.resources.piece_pawn_w
import memorchess.composeapp.generated.resources.piece_queen_b
import memorchess.composeapp.generated.resources.piece_queen_w
import memorchess.composeapp.generated.resources.piece_rook_b
import memorchess.composeapp.generated.resources.piece_rook_w
import org.jetbrains.compose.resources.painterResource
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.core.engine.Player

/**
 * Preloaded [Painter]s for the 12 chess pieces, so a board remount is a map lookup, not 32 async
 * loads. Read-tracked, so each async painter resolution recomposes only the readers, not the theme
 * subtree.
 */
val LocalPiecePainters =
  compositionLocalOf<Map<ChessPiece, Painter>> {
    error("LocalPiecePainters not provided — wrap content in PiecePaintersProvider")
  }

/**
 * Resolves all 12 piece painters for [LocalPiecePainters]. Mount at theme scope so the cache
 * survives page navigation.
 */
@Composable
fun rememberPiecePainters(): Map<ChessPiece, Painter> {
  val kingW = painterResource(Res.drawable.piece_king_w)
  val queenW = painterResource(Res.drawable.piece_queen_w)
  val rookW = painterResource(Res.drawable.piece_rook_w)
  val bishopW = painterResource(Res.drawable.piece_bishop_w)
  val knightW = painterResource(Res.drawable.piece_knight_w)
  val pawnW = painterResource(Res.drawable.piece_pawn_w)
  val kingB = painterResource(Res.drawable.piece_king_b)
  val queenB = painterResource(Res.drawable.piece_queen_b)
  val rookB = painterResource(Res.drawable.piece_rook_b)
  val bishopB = painterResource(Res.drawable.piece_bishop_b)
  val knightB = painterResource(Res.drawable.piece_knight_b)
  val pawnB = painterResource(Res.drawable.piece_pawn_b)
  return remember(
    kingW,
    queenW,
    rookW,
    bishopW,
    knightW,
    pawnW,
    kingB,
    queenB,
    rookB,
    bishopB,
    knightB,
    pawnB,
  ) {
    mapOf(
      ChessPiece(PieceKind.KING, Player.WHITE) to kingW,
      ChessPiece(PieceKind.QUEEN, Player.WHITE) to queenW,
      ChessPiece(PieceKind.ROOK, Player.WHITE) to rookW,
      ChessPiece(PieceKind.BISHOP, Player.WHITE) to bishopW,
      ChessPiece(PieceKind.KNIGHT, Player.WHITE) to knightW,
      ChessPiece(PieceKind.PAWN, Player.WHITE) to pawnW,
      ChessPiece(PieceKind.KING, Player.BLACK) to kingB,
      ChessPiece(PieceKind.QUEEN, Player.BLACK) to queenB,
      ChessPiece(PieceKind.ROOK, Player.BLACK) to rookB,
      ChessPiece(PieceKind.BISHOP, Player.BLACK) to bishopB,
      ChessPiece(PieceKind.KNIGHT, Player.BLACK) to knightB,
      ChessPiece(PieceKind.PAWN, Player.BLACK) to pawnB,
    )
  }
}
