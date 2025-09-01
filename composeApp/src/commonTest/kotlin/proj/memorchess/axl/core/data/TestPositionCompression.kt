package proj.memorchess.axl.core.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import proj.memorchess.axl.core.engine.Game

class TestPositionCompression {

  private fun nibbleAt(bytes: ByteArray, row: Int, col: Int): Int {
    // same indexing as implementation: idx = (7 - row) * 8 + col
    val idx = (7 - row) * 8 + col
    val byteIndex = idx / 2
    val low = (idx % 2 == 0)
    val b = bytes[byteIndex].toInt() and 0xFF
    return if (low) b and 0xF else (b ushr 4) and 0xF
  }

  @Test
  fun size_is_32_bytes() {
    val id = PositionIdentifier("8/8/8/8/8/8/8/8 w - - 0 1")
    val compressed = CompressedPositionIdentifier.fromPositionIdentifier(id)
    assertEquals(32, compressed.bytes.size, "Compressed blob must be 32 bytes")
  }

  @Test
  fun empty_board_all_zeroes() {
    val id = PositionIdentifier("8/8/8/8/8/8/8/8 w - - 0 1")
    val compressed = CompressedPositionIdentifier.fromPositionIdentifier(id)
    assertTrue(
      compressed.bytes.all { it.toInt() == 0 },
      "All nibbles/bytes should be zero on empty board",
    )
  }

  @Test
  fun starting_position_basic_checks() {
    val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    val id = PositionIdentifier(fen)
    val c = CompressedPositionIdentifier.fromPositionIdentifier(id)

    // Pawns: white row=1, black row=6. Code: white pawn 0x1, black pawn 0x9
    for (col in 0 until 8) {
      assertEquals(0x1, nibbleAt(c.bytes, 1, col), "White pawn at row1 col=$col")
      assertEquals(0x9, nibbleAt(c.bytes, 6, col), "Black pawn at row6 col=$col")
    }

    // Back ranks pieces and colors. Rooks that can castle should be 0x7 for white, 0xF for black
    // White back rank is row 0 in board coordinates per FenParser usage
    assertEquals(0x7, nibbleAt(c.bytes, 0, 0), "White rook a1 with castling flag")
    assertEquals(0x7, nibbleAt(c.bytes, 0, 7), "White rook h1 with castling flag")
    assertEquals(0x3, nibbleAt(c.bytes, 0, 1), "White knight b1")
    assertEquals(0x3, nibbleAt(c.bytes, 0, 6), "White knight g1")
    assertEquals(0x4, nibbleAt(c.bytes, 0, 2), "White bishop c1")
    assertEquals(0x4, nibbleAt(c.bytes, 0, 5), "White bishop f1")
    assertEquals(0x6, nibbleAt(c.bytes, 0, 3), "White queen d1")
    assertEquals(0x5, nibbleAt(c.bytes, 0, 4), "White king e1")

    // Black back rank is row 7
    assertEquals(0xF, nibbleAt(c.bytes, 7, 0), "Black rook a8 with castling flag and color bit")
    assertEquals(0xF, nibbleAt(c.bytes, 7, 7), "Black rook h8 with castling flag and color bit")
    assertEquals(0xB, nibbleAt(c.bytes, 7, 1), "Black knight b8 -> 0x3 | 0x8 = 0xB")
    assertEquals(0xB, nibbleAt(c.bytes, 7, 6), "Black knight g8")
    assertEquals(0xC, nibbleAt(c.bytes, 7, 2), "Black bishop c8 -> 0x4 | 0x8 = 0xC")
    assertEquals(0xC, nibbleAt(c.bytes, 7, 5), "Black bishop f8")
    assertEquals(0xE, nibbleAt(c.bytes, 7, 3), "Black queen d8 -> 0x6 | 0x8 = 0xE")
    assertEquals(0xD, nibbleAt(c.bytes, 7, 4), "Black king e8 -> 0x5 | 0x8 = 0xD")
  }

  @Test
  fun en_passant_mark_for_white_to_move_sets_row5_target() {
    // Example with en-passant column c (2): position indicates en passant at c6 for white to move
    // in examples
    val fen = "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2"
    val c = CompressedPositionIdentifier.fromPositionIdentifier(PositionIdentifier(fen))
    // For white to move, code marks row=5 (rank 6) and column = enPassantColumn (file c = 2) with
    // 0x8
    assertEquals(0x8, nibbleAt(c.bytes, 5, 2), "En passant mark must be 0x8 at (row5,col2)")
  }

  @Test
  fun en_passant_mark_for_black_to_move_sets_row2_target() {
    val fen =
      "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1" // en passant column e (4)
    val c = CompressedPositionIdentifier.fromPositionIdentifier(PositionIdentifier(fen))
    // For black to move, row should be 2 and column 4
    assertEquals(0x8, nibbleAt(c.bytes, 2, 4), "En passant mark must be 0x8 at (row2,col4)")
  }

  @Test
  fun test_all_pieces_and_colors() {
    val fen = "rnbqkbnr/pppppppp/8/8/2P5/8/PP1PPPPP/RNBQKBNR b KQkq"
    val c = CompressedPositionIdentifier.fromPositionIdentifier(PositionIdentifier(fen))
  }

  companion object {
    fun testOnGame(game: Game) {
      assertEquals(
        game.position.createIdentifier(),
        CompressedPositionIdentifier.fromPositionIdentifier(game.position.createIdentifier())
          .toPosition()
          .createIdentifier(),
      )
    }
  }
}
