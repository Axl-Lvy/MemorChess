package proj.memorchess.axl.core.engine

import io.github.alluhemanth.chess.core.ChessGame
import io.github.alluhemanth.chess.core.board.File
import io.github.alluhemanth.chess.core.board.Rank
import io.github.alluhemanth.chess.core.board.Square
import io.github.alluhemanth.chess.core.piece.PieceColor
import io.github.alluhemanth.chess.core.piece.PieceType
import io.github.alluhemanth.chess.core.utils.SanUtils
import proj.memorchess.axl.core.data.PositionKey

/**
 * Wrapper around [ChessGame] from chess-core-kmp that provides a simpler API for the app.
 *
 * Manages board state, validates moves (SAN and coordinate-based), handles promotions, and produces
 * [PositionKey]s for node storage. SAN strings returned by move methods never include check or
 * checkmate markers (`+`/`#`), for consistency with stored move data.
 */
class GameEngine private constructor(private val game: ChessGame) {

  /** Creates a new game from the standard starting position. */
  constructor() : this(ChessGame())

  /**
   * Creates a new game from the given [PositionKey].
   *
   * Accepts cropped FENs (3–5 parts) as stored by [PositionKey]; missing fields (en passant,
   * half-move clock, full-move number) are filled with defaults.
   */
  constructor(
    positionKey: PositionKey
  ) : this(ChessGame().also { it.loadFen(toFullFen(positionKey.value)) })

  /** The player whose turn it is to move. */
  val playerTurn: Player
    get() = if (game.getCurrentPlayer() == PieceColor.WHITE) Player.WHITE else Player.BLACK

  /**
   * Plays a move given in Standard Algebraic Notation (e.g. "e4", "Nf3", "O-O").
   *
   * @throws IllegalMoveException if the move is not legal in the current position.
   */
  fun playSanMove(san: String) {
    val success = game.makeSanMove(san)
    if (!success) {
      throw IllegalMoveException("Illegal SAN move: $san")
    }
  }

  /**
   * Plays a non-promotion move identified by board coordinates.
   *
   * @param from source square as (row, col) where row 0 = rank 1, col 0 = file a.
   * @param to destination square in the same coordinate system.
   * @return the SAN string of the move played (without check/checkmate markers).
   * @throws IllegalMoveException if no legal non-promotion move matches the coordinates.
   */
  fun playCoordinateMove(from: Pair<Int, Int>, to: Pair<Int, Int>): String {
    val fromSquare = toSquare(from)
    val toSquare = toSquare(to)
    val legalMoves = game.getLegalMoves()
    val move =
      legalMoves.firstOrNull {
        it.from == fromSquare && it.to == toSquare && it.promotionPieceType == null
      }
        ?: throw IllegalMoveException(
          "No legal move from ${BoardUtils.tileName(from)} to ${BoardUtils.tileName(to)}"
        )
    val san = SanUtils.moveToSan(move, game.getBoard(), game.getGameState()).stripCheckMarkers()
    val success = game.makeMove(move)
    if (!success) {
      throw IllegalMoveException("Move rejected by engine")
    }
    return san
  }

  /**
   * Plays a promotion move identified by board coordinates and the chosen promotion piece.
   *
   * @param from source square as (row, col).
   * @param to destination square as (row, col).
   * @param promotion the piece kind to promote to (typically QUEEN, ROOK, BISHOP, or KNIGHT).
   * @return the SAN string of the promotion move played (without check/checkmate markers).
   * @throws IllegalMoveException if no legal promotion move matches the coordinates and piece.
   */
  fun playCoordinateMoveWithPromotion(
    from: Pair<Int, Int>,
    to: Pair<Int, Int>,
    promotion: PieceKind,
  ): String {
    val fromSquare = toSquare(from)
    val toSquare = toSquare(to)
    val promType = toChessCorePieceType(promotion)
    val legalMoves = game.getLegalMoves()
    val move =
      legalMoves.firstOrNull {
        it.from == fromSquare && it.to == toSquare && it.promotionPieceType == promType
      } ?: throw IllegalMoveException("No legal promotion move")
    val san = SanUtils.moveToSan(move, game.getBoard(), game.getGameState()).stripCheckMarkers()
    val success = game.makeMove(move)
    if (!success) {
      throw IllegalMoveException("Promotion move rejected by engine")
    }
    return san
  }

  /** Returns `true` if a move from [from] to [to] would be a pawn promotion. */
  fun isPromotionMove(from: Pair<Int, Int>, to: Pair<Int, Int>): Boolean {
    val fromSquare = toSquare(from)
    val toSquare = toSquare(to)
    val legalMoves = game.getLegalMoves()
    return legalMoves.any {
      it.from == fromSquare && it.to == toSquare && it.promotionPieceType != null
    }
  }

  /** Returns the piece at the given board coordinates, or `null` if the square is empty. */
  fun pieceAt(row: Int, col: Int): ChessPiece? {
    val square = Square(File('a' + col), Rank(row + 1))
    val piece = game.getBoard()[square] ?: return null
    return ChessPiece(fromChessCorePieceType(piece.pieceType), fromChessCoreColor(piece.color))
  }

  /** Returns the full 6-part [Fen] of the current position. */
  fun toFen(): Fen = Fen(game.getFen())

  /**
   * Builds a [PositionKey] for the current board state.
   *
   * The key contains a cropped FEN (board + turn + castling, and en passant only when an adjacent
   * enemy pawn can actually capture). This ensures that positions which are identical for
   * repertoire purposes share the same key regardless of move counters.
   */
  fun toPositionKey(): PositionKey {
    return PositionKey(toCroppedFenString())
  }

  /**
   * Produces the cropped FEN used by [toPositionKey]. Includes en passant only when
   * [isEnPassantRelevant] — i.e. an adjacent pawn of the current player can actually capture.
   */
  private fun toCroppedFenString(): String {
    val fen = game.getFen()
    val parts = fen.split(" ")
    val board = parts[0]
    val turn = parts[1]
    val castling = parts[2]
    val enPassant = parts[3]

    val base = "$board $turn $castling"
    return if (enPassant != "-" && isEnPassantRelevant(parts)) {
      "$base $enPassant"
    } else {
      base
    }
  }

  /**
   * Checks whether the en passant square in the FEN is actually capturable — i.e. there is an
   * adjacent pawn of the current player on the correct rank.
   */
  private fun isEnPassantRelevant(fenParts: List<String>): Boolean {
    val enPassantSquare = fenParts[3]
    if (enPassantSquare == "-") return false

    val epCol = enPassantSquare[0] - 'a'
    // White to move → en passant target on rank 6, pawns on rank 5 (row index 4)
    // Black to move → en passant target on rank 3, pawns on rank 4 (row index 3)
    val checkingRow = if (fenParts[1] == "w") 4 else 3
    val currentPlayer = if (fenParts[1] == "w") Player.WHITE else Player.BLACK

    if (epCol > 0) {
      val piece = pieceAt(checkingRow, epCol - 1)
      if (piece != null && piece.kind == PieceKind.PAWN && piece.player == currentPlayer) {
        return true
      }
    }
    if (epCol < 7) {
      val piece = pieceAt(checkingRow, epCol + 1)
      if (piece != null && piece.kind == PieceKind.PAWN && piece.player == currentPlayer) {
        return true
      }
    }
    return false
  }

  companion object {
    /** Strips check (`+`) and checkmate (`#`) suffixes from a SAN string. */
    private fun String.stripCheckMarkers(): String = trimEnd('+', '#')

    /** Converts (row, col) coordinates to a chess-core [Square]. */
    private fun toSquare(coords: Pair<Int, Int>): Square {
      return Square(File('a' + coords.second), Rank(coords.first + 1))
    }

    private fun toChessCorePieceType(kind: PieceKind): PieceType =
      when (kind) {
        PieceKind.KING -> PieceType.KING
        PieceKind.QUEEN -> PieceType.QUEEN
        PieceKind.ROOK -> PieceType.ROOK
        PieceKind.BISHOP -> PieceType.BISHOP
        PieceKind.KNIGHT -> PieceType.KNIGHT
        PieceKind.PAWN -> PieceType.PAWN
      }

    private fun fromChessCorePieceType(type: PieceType): PieceKind =
      when (type) {
        PieceType.KING -> PieceKind.KING
        PieceType.QUEEN -> PieceKind.QUEEN
        PieceType.ROOK -> PieceKind.ROOK
        PieceType.BISHOP -> PieceKind.BISHOP
        PieceType.KNIGHT -> PieceKind.KNIGHT
        PieceType.PAWN -> PieceKind.PAWN
      }

    private fun fromChessCoreColor(color: PieceColor): Player =
      when (color) {
        PieceColor.WHITE -> Player.WHITE
        PieceColor.BLACK -> Player.BLACK
      }

    /**
     * Converts a cropped FEN (board + turn + castling [+ en passant]) to a full 6-part FEN by
     * appending default half-move clock (0) and full-move number (1) if missing.
     *
     * A fen with less than 5 parts is invalid
     */
    private fun toFullFen(fen: String): String {
      val parts = fen.trim().split(" ")
      return when (parts.size) {
        6 -> fen
        3 -> "$fen - 0 1"
        4 -> "$fen 0 1"
        5 -> "$fen 1"
        else -> fen // let chess-core handle the error
      }
    }
  }
}
