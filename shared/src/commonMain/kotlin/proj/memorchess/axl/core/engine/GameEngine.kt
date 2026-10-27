package proj.memorchess.axl.core.engine

import io.github.alluhemanth.chess.core.ChessGame
import io.github.alluhemanth.chess.core.board.File
import io.github.alluhemanth.chess.core.board.Rank
import io.github.alluhemanth.chess.core.board.Square
import io.github.alluhemanth.chess.core.move.Move
import io.github.alluhemanth.chess.core.piece.PieceColor
import io.github.alluhemanth.chess.core.piece.PieceType
import io.github.alluhemanth.chess.core.utils.SanUtils
import proj.memorchess.axl.core.data.PositionKey

/**
 * Chess game that validates and plays moves and produces [PositionKey]s. Returned SAN never carries
 * check or checkmate markers, matching stored move data.
 */
class GameEngine private constructor(private val game: ChessGame) {

  /** Creates a new game from the standard starting position. */
  constructor() : this(ChessGame())

  /** Creates a new game from a [PositionKey], filling the cropped FEN fields with defaults. */
  constructor(
    positionKey: PositionKey
  ) : this(ChessGame().also { it.loadFen(toFullFen(positionKey.value)) })

  /** The player whose turn it is to move. */
  val playerTurn: Player
    get() = if (game.getCurrentPlayer() == PieceColor.WHITE) Player.WHITE else Player.BLACK

  /**
   * Plays a move given in Standard Algebraic Notation (e.g. "e4", "Nf3", "O-O").
   *
   * @return the SAN of the move played as [playCoordinateMove] would spell it, so an
   *   over-disambiguated input such as "Ngf3" comes back as "Nf3".
   * @throws IllegalMoveException if the move is not legal in the current position.
   */
  fun playSanMove(san: String): String {
    val move = legalMove(san)
    val canonical =
      SanUtils.moveToSan(move, game.getBoard(), game.getGameState()).stripCheckMarkers()
    if (!game.makeMove(move)) {
      throw IllegalMoveException("Illegal SAN move: $san")
    }
    return canonical
  }

  private fun legalMove(san: String): Move {
    // chess-core rejects some moves by throwing; normalize them into IllegalMoveException.
    val parsed =
      try {
        SanUtils.sanToMove(san, game.getBoard(), game.getGameState())
      } catch (e: RuntimeException) {
        throw IllegalMoveException("Illegal SAN move: $san", e)
      }
    return game.getLegalMoves().firstOrNull {
      it.from == parsed.from &&
        it.to == parsed.to &&
        it.promotionPieceType == parsed.promotionPieceType
    } ?: throw IllegalMoveException("Illegal SAN move: $san")
  }

  /**
   * Plays a non-promotion move identified by board coordinates.
   *
   * @param from source square as (row, col) where row 0 = rank 1, col 0 = file a; [to] likewise.
   * @return the SAN of the move played.
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
   * @return the SAN of the move played.
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

  /**
   * Returns the square the piece moved by [san] starts from, without playing it.
   *
   * @throws IllegalMoveException if [san] is not a legal move in the current position.
   */
  fun originSquare(san: String): BoardLocation {
    val move = legalMove(san)
    return BoardLocation(row = move.from.rank.value - 1, col = move.from.file.value - 'a')
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
   * Builds a [PositionKey] from the board, turn, castling, and en passant only when a capture is
   * possible, so move counters never split one position into several keys.
   */
  fun toPositionKey(): PositionKey {
    return PositionKey(toCroppedFenString())
  }

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

  /** Whether a pawn of the side to move can actually capture en passant. */
  private fun isEnPassantRelevant(fenParts: List<String>): Boolean {
    val enPassantSquare = fenParts[3]
    if (enPassantSquare == "-") return false

    val epCol = enPassantSquare[0] - 'a'
    // Capturing pawns stand on rank 5 (row 4) for White, rank 4 (row 3) for Black.
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
    private fun String.stripCheckMarkers(): String = trimEnd('+', '#')

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

    /** Pads a cropped FEN to six parts with a zero half-move clock and full-move number 1. */
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
