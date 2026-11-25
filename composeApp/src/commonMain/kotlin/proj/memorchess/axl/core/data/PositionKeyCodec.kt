package proj.memorchess.axl.core.data

/**
 * Lossless binary storage form of a [PositionKey], used as the key column of every local position
 * table.
 *
 * A key packs into the layout of Lichess `BinaryFen` without its counters: an 8 byte big-endian
 * occupancy bitboard (`a1` is bit 0) followed by one nibble per occupied square, low nibble first,
 * at most 24 bytes. Anything that does not survive that round trip exactly (malformed text, a
 * castling right without its rook) is stored escaped: 8 zero bytes then the key's UTF-8 text. A
 * packed key always has a non zero occupancy, so the two forms never collide.
 */
object PositionKeyCodec {

  /** Ascending order of the encoded bytes, compared unsigned and shorter first. */
  val ORDER: Comparator<PositionKey> = Comparator { a, b -> compareUnsigned(encode(a), encode(b)) }

  /** Encodes [key], packed when it round trips exactly and escaped otherwise. */
  fun encode(key: PositionKey): ByteArray {
    val packed = pack(key.value)
    return if (packed != null && unpack(packed) == key.value) packed else escape(key.value)
  }

  /** Decodes bytes produced by [encode] back into the original key. */
  fun decode(bytes: ByteArray): PositionKey =
    PositionKey(
      if (isEscaped(bytes)) bytes.decodeToString(OCCUPANCY_BYTES, bytes.size) else unpack(bytes),
    )

  private const val OCCUPANCY_BYTES = 8
  private const val PIECE_CHARS = "PpNnBbRrQqKk"
  private const val EN_PASSANT_PAWN = 12
  private const val CASTLING_WHITE_ROOK = 13
  private const val CASTLING_BLACK_ROOK = 14
  private const val BLACK_KING_TO_MOVE = 15
  private const val WHITE_ROOK = 6
  private const val BLACK_ROOK = 7
  private const val BLACK_KING = 11
  private const val A1 = 0
  private const val H1 = 7
  private const val A8 = 56
  private const val H8 = 63

  private fun escape(value: String): ByteArray =
    ByteArray(OCCUPANCY_BYTES) + value.encodeToByteArray()

  private fun isEscaped(bytes: ByteArray): Boolean =
    bytes.size >= OCCUPANCY_BYTES && (0 until OCCUPANCY_BYTES).all { bytes[it] == 0.toByte() }

  /** Packs a cropped FEN, or returns `null` when it cannot be parsed into the packed layout. */
  private fun pack(fen: String): ByteArray? {
    val fields = fen.split(' ')
    if (fields.size !in 3..4) return null
    val codes = parseBoard(fields[0]) ?: return null
    if (!applyCastling(codes, fields[2])) return null
    if (fields.size == 4 && !applyEnPassant(codes, fields[3])) return null
    when (fields[1]) {
      "w" -> Unit
      "b" -> {
        val king = codes.indexOf(BLACK_KING)
        if (king < 0) return null
        codes[king] = BLACK_KING_TO_MOVE
      }
      else -> return null
    }
    return write(codes)
  }

  private fun parseBoard(board: String): IntArray? {
    val ranks = board.split('/')
    if (ranks.size != 8) return null
    val codes = IntArray(64) { -1 }
    for ((index, rank) in ranks.withIndex()) {
      val rankStart = (7 - index) * 8
      var file = 0
      for (c in rank) {
        if (c in '1'..'8') {
          file += c - '0'
        } else {
          val code = PIECE_CHARS.indexOf(c)
          if (code < 0 || file > 7) return null
          codes[rankStart + file] = code
          file++
        }
      }
      if (file != 8) return null
    }
    return codes
  }

  private fun applyCastling(codes: IntArray, castling: String): Boolean {
    if (castling == "-") return true
    for (right in castling) {
      val (square, rook, marked) =
        when (right) {
          'K' -> Triple(H1, WHITE_ROOK, CASTLING_WHITE_ROOK)
          'Q' -> Triple(A1, WHITE_ROOK, CASTLING_WHITE_ROOK)
          'k' -> Triple(H8, BLACK_ROOK, CASTLING_BLACK_ROOK)
          'q' -> Triple(A8, BLACK_ROOK, CASTLING_BLACK_ROOK)
          else -> return false
        }
      if (codes[square] != rook) return false
      codes[square] = marked
    }
    return true
  }

  private fun applyEnPassant(codes: IntArray, target: String): Boolean {
    if (target.length != 2 || target[0] !in 'a'..'h') return false
    val file = target[0] - 'a'
    val pawnSquare =
      when (target[1]) {
        '6' -> 4 * 8 + file
        '3' -> 3 * 8 + file
        else -> return false
      }
    val expectedPawn = if (target[1] == '6') PIECE_CHARS.indexOf('p') else PIECE_CHARS.indexOf('P')
    if (codes[pawnSquare] != expectedPawn) return false
    codes[pawnSquare] = EN_PASSANT_PAWN
    return true
  }

  private fun write(codes: IntArray): ByteArray? {
    var occupancy = 0L
    var count = 0
    for (square in 0 until 64) {
      if (codes[square] >= 0) {
        occupancy = occupancy or (1L shl square)
        count++
      }
    }
    if (occupancy == 0L) return null
    val bytes = ByteArray(OCCUPANCY_BYTES + (count + 1) / 2)
    for (i in 0 until OCCUPANCY_BYTES) bytes[i] = (occupancy ushr (56 - 8 * i)).toByte()
    var nibble = 0
    for (square in 0 until 64) {
      val code = codes[square]
      if (code < 0) continue
      val index = OCCUPANCY_BYTES + nibble / 2
      val shifted = if (nibble % 2 == 0) code else code shl 4
      bytes[index] = (bytes[index].toInt() or shifted).toByte()
      nibble++
    }
    return bytes
  }

  private fun unpack(bytes: ByteArray): String {
    val occupancy = readOccupancy(bytes)
    val position = UnpackedPosition()
    var nibble = 0
    for (square in 0 until 64) {
      if (occupancy and (1L shl square) != 0L) {
        position.place(square, nibbleAt(bytes, nibble))
        nibble++
      }
    }
    return position.toFen()
  }

  private fun readOccupancy(bytes: ByteArray): Long {
    var occupancy = 0L
    for (i in 0 until OCCUPANCY_BYTES) {
      occupancy = (occupancy shl 8) or (bytes[i].toLong() and 0xFF)
    }
    return occupancy
  }

  private fun nibbleAt(bytes: ByteArray, nibble: Int): Int {
    val byte = bytes[OCCUPANCY_BYTES + nibble / 2].toInt()
    return (if (nibble % 2 == 0) byte else byte shr 4) and 0x0F
  }

  /** Board, side to move, castling rights and en passant target rebuilt from packed nibbles. */
  private class UnpackedPosition {
    private val board = CharArray(64)
    private var blackToMove = false
    private val castling = BooleanArray(4)
    private var enPassant = -1

    fun place(square: Int, code: Int) {
      board[square] =
        when (code) {
          EN_PASSANT_PAWN -> placeEnPassantPawn(square)
          CASTLING_WHITE_ROOK -> placeCastlingRook(if (square == H1) 0 else 1, 'R')
          CASTLING_BLACK_ROOK -> placeCastlingRook(if (square == H8) 2 else 3, 'r')
          BLACK_KING_TO_MOVE -> {
            blackToMove = true
            'k'
          }
          else -> PIECE_CHARS[code]
        }
    }

    /** A pawn on rank 4 is white and was pushed from rank 2; otherwise it is black on rank 5. */
    private fun placeEnPassantPawn(square: Int): Char {
      val white = square < 32
      enPassant = if (white) square - 8 else square + 8
      return if (white) 'P' else 'p'
    }

    private fun placeCastlingRook(right: Int, rook: Char): Char {
      castling[right] = true
      return rook
    }

    fun toFen(): String = buildString {
      for (rank in 7 downTo 0) {
        appendRank(rank)
        if (rank > 0) append('/')
      }
      append(if (blackToMove) " b " else " w ")
      append("KQkq".filterIndexed { i, _ -> castling[i] }.ifEmpty { "-" })
      if (enPassant >= 0) append(' ').append('a' + enPassant % 8).append(enPassant / 8 + 1)
    }

    private fun StringBuilder.appendRank(rank: Int) {
      var empty = 0
      for (file in 0 until 8) {
        val piece = board[rank * 8 + file]
        if (piece == EMPTY) {
          empty++
          continue
        }
        if (empty > 0) append(empty)
        empty = 0
        append(piece)
      }
      if (empty > 0) append(empty)
    }

    private companion object {
      const val EMPTY = '\u0000'
    }
  }

  private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
    for (i in 0 until minOf(a.size, b.size)) {
      val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
      if (diff != 0) return diff
    }
    return a.size - b.size
  }
}
