package proj.memorchess.axl.core.engine

/** The type of a chess piece, independent of color. */
enum class PieceKind {
  KING,
  QUEEN,
  ROOK,
  BISHOP,
  KNIGHT,
  PAWN,
}

/** A chess piece of [kind] owned by [player]; [toString] gives its FEN letter. */
data class ChessPiece(val kind: PieceKind, val player: Player) {
  override fun toString(): String {
    val c =
      when (kind) {
        PieceKind.KING -> 'K'
        PieceKind.QUEEN -> 'Q'
        PieceKind.ROOK -> 'R'
        PieceKind.BISHOP -> 'B'
        PieceKind.KNIGHT -> 'N'
        PieceKind.PAWN -> 'P'
      }
    return if (player == Player.WHITE) c.uppercaseChar().toString()
    else c.lowercaseChar().toString()
  }
}
