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
 * Cache of resolved [Painter]s for the 12 distinct chess pieces, keyed by [ChessPiece].
 *
 * The board renders up to 32 pieces simultaneously and remounts every time the user switches pages.
 * Calling [painterResource] per tile would spawn 32 async resource loads on each remount;
 * preloading once at the theme layer turns mounts into synchronous map lookups so the board appears
 * in a single frame.
 *
 * Backed by [compositionLocalOf] (read-tracked) rather than `staticCompositionLocalOf` so painter
 * resolutions during app startup invalidate only the [Piece] composables that read the cache — with
 * a static local, the entire subtree under the theme recomposed on each of the 12 async painter
 * resolutions and visibly froze the initial loading indicator.
 */
val LocalPiecePainters =
  compositionLocalOf<Map<ChessPiece, Painter>> {
    error("LocalPiecePainters not provided — wrap content in PiecePaintersProvider")
  }

/**
 * Eagerly resolves all 12 chess-piece painters and provides them via [LocalPiecePainters]. Mount
 * once at app/theme scope so the cache survives page navigation; calls to [painterResource] inside
 * are de-duped by Compose Resources so this is cheap to re-enter on theme changes.
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
