package proj.memorchess.axl.core.data

import kotlinx.serialization.Serializable
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.board.BoardLocation
import proj.memorchess.axl.core.engine.board.IBoard
import proj.memorchess.axl.core.engine.parser.FenParser

/**
 * Compact representation of a chess position intended for storage.
 *
 * Encoding uses 4 bits per tile (nibble) packed as 2 tiles per byte, for 64 tiles -> 32 bytes. Bit
 * mapping (low 4 bits first per tile):
 * - 0000: empty
 * - 0001: pawn
 * - 0010: rook
 * - 0011: knight
 * - 0100: bishop
 * - 0101: king
 * - 0110: queen
 * - 0111: rook that can castle Additionally, the highest bit (bit 3) of each nibble indicates
 *   color: 1 = black, 0 = white. The code list above already assumes this by setting the MSB to 1
 *   for black pieces.
 *
 * Special cases:
 * - 1000 on an empty tile marks a file that can be captured en passant (only one tile will carry
 *   this mark).
 */
@Serializable
class CompressedPositionIdentifier(val bytes: ByteArray) {
  init {
    require(bytes.size == 32) {
      "CompressedPositionIdentifier must be exactly 32 bytes (got ${bytes.size})."
    }
  }

  fun toPosition(): proj.memorchess.axl.core.engine.board.Position {
    // Decode the 32-byte buffer back into a Position
    val board = proj.memorchess.axl.core.engine.board.Board()
    var playerTurn = Game.Player.WHITE
    val possibleCastles = arrayOf(false, false, false, false)
    var enPassantColumn = -1

    for (idx in 0 until 64) {
      val byteIndex = idx / 2
      val isLow = (idx % 2 == 0)
      val b = bytes[byteIndex].toInt() and 0xFF
      val nibble = if (isLow) (b and 0xF) else ((b ushr 4) and 0xF)

      val row = 7 - (idx / 8)
      val col = idx % 8

      if (nibble == 0x0) continue

      // En passant marker: 1000 with no piece bits set
      if (nibble == 0x8) {
        if (row == 5 || row == 2) {
          enPassantColumn = col
        } else {
          playerTurn = Game.Player.BLACK
        }
        continue
      }

      val colorIsBlack = (nibble and 0x8) != 0
      val base = nibble and 0x7

      // Map base code back to piece character
      val pieceCharLower =
        when (base) {
          0x1 -> "p"
          0x2,
          0x7 -> "r" // 0x7 indicates rook that can castle
          0x3 -> "n"
          0x4 -> "b"
          0x5 -> "k"
          0x6 -> "q"
          else -> null
        }

      if (pieceCharLower != null) {
        val pieceStr = if (colorIsBlack) pieceCharLower else pieceCharLower.uppercase()
        board.placePiece(row, col, pieceStr)

        // Reconstruct castling rights from special rook code (0x7)
        if (base == 0x7) {
          if (!colorIsBlack) {
            // White rooks: a1 (row 0, col 0) -> Q-side [1], h1 (row 0, col 7) -> K-side [0]
            if (row == 0 && col == 0) possibleCastles[1] = true
            if (row == 0 && col == 7) possibleCastles[0] = true
          } else {
            // Black rooks: a8 (row 7, col 0) -> q-side [3], h8 (row 7, col 7) -> k-side [2]
            if (row == 7 && col == 0) possibleCastles[3] = true
            if (row == 7 && col == 7) possibleCastles[2] = true
          }
        }
      }
    }

    return proj.memorchess.axl.core.engine.board.Position(
      board,
      playerTurn,
      possibleCastles,
      enPassantColumn,
    )
  }

  companion object {
    private var firstEmptyTile: BoardLocation? = null

    fun fromPositionIdentifier(id: PositionIdentifier): CompressedPositionIdentifier =
      fromFen(id.fenRepresentation)

    fun fromFen(positionFen: String): CompressedPositionIdentifier {
      // Leverage existing FenParser to read an IPosition.
      val position = FenParser.readPosition(PositionIdentifier(positionFen))
      val board = position.board
      val result = ByteArray(32)

      // We pack tiles in row-major order from a8 to h1 consistently with FEN scan order (top row to
      // bottom).
      // FenParser.readBoard reads ranks from 8 to 1 mapping to rows 7..0; here we iterate row 7..0
      // and col 0..7.
      var byteIndex = 0
      for (i in 0 until 64 step 2) {
        val row1 = 7 - (i / 8)
        val col1 = i % 8
        val row2 = 7 - ((i + 1) / 8)
        val col2 = (i + 1) % 8
        val nibble1 = encodeTile(board, row1, col1, position.possibleCastles)
        val nibble2 = encodeTile(board, row2, col2, position.possibleCastles)
        val packed = ((nibble2 and 0xF) shl 4) or (nibble1 and 0xF)
        result[byteIndex++] = packed.toByte()
      }

      // En passant: if position has a valid en passant column, we will mark it.
      if (position.enPassantColumn >= 0) {
        // En Passant target square depends on side to move. In FEN, the target square is the square
        // behind the pawn that just advanced two squares. Our Position exposes only the column, not
        // the row.
        // We'll mark the file by setting 1000 on the nibble of the topmost empty tile in that file
        // that corresponds to the en-passant target, depending on player turn.
        val col = position.enPassantColumn
        val row = if (position.playerTurn == Game.Player.WHITE) 5 else 2
        markEnPassant(result, row, col)
      }
      val firstEmptyTileSnapshot = firstEmptyTile
      checkNotNull(firstEmptyTileSnapshot) {
        "Invariant: firstEmptyTile should have been set when encoding position, but was null. Position FEN: ${position.createIdentifier().fenRepresentation}"
      }
      if (position.playerTurn == Game.Player.BLACK) {
        markEnPassant(result, firstEmptyTileSnapshot.row, firstEmptyTileSnapshot.col)
      }
      firstEmptyTile = null

      return CompressedPositionIdentifier(result)
    }

    private fun encodeTile(
      board: IBoard,
      row: Int,
      col: Int,
      possibleCastles: Array<Boolean>,
    ): Int {
      val tile = board.getTile(row, col)
      val piece = tile.getSafePiece() // 0000 empty
      val isBlack = piece?.player == Game.Player.BLACK
      val type = piece.toString().lowercase()
      val base =
        when (type) {
          "p" -> 0x1
          "r" -> {
            val rookCastle =
              (row == 0 && col == 0 && !isBlack && possibleCastles[1]) ||
                (row == 0 && col == 7 && !isBlack && possibleCastles[0]) ||
                (row == 7 && col == 0 && isBlack && possibleCastles[3]) ||
                (row == 7 && col == 7 && isBlack && possibleCastles[2])
            if (rookCastle) 0x7 else 0x2
          }
          "n" -> 0x3
          "b" -> 0x4
          "k" -> 0x5
          "q" -> 0x6
          else -> {
            if (row != 5 && row != 2) {
              firstEmptyTile = BoardLocation(row, col)
            }
            0x0
          }
        }
      return if (isBlack) (base or 0x8) else base
    }

    private fun markEnPassant(bytes: ByteArray, row: Int, col: Int) {
      // Find the nibble index (0..63) in our packing order: i corresponds to row-major from a8..h1
      val idx = (7 - row) * 8 + col
      val byteIndex = idx / 2
      val isLow = (idx % 2 == 0)
      val current = bytes[byteIndex].toInt() and 0xFF
      val mark = 0x8 // 1000
      val newByte =
        if (isLow) {
          (current and 0xF0) or mark
        } else {
          (current and 0x0F) or (mark shl 4)
        }
      bytes[byteIndex] = newByte.toByte()
    }
  }
}

private class PositionCompressor() {}
