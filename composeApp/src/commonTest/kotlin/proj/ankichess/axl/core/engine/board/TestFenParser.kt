package proj.ankichess.axl.core.engine.board

import kotlin.test.Test
import kotlin.test.assertEquals
import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.impl.engine.parser.FenParser

class TestFenParser {

  @Test
  fun testStartingPosition() {
    val game = Game()
    assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", FenParser.parse(game))
  }

  @Test
  fun testReadParse() {
    val fensToTest =
      listOf(
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2",
        "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2",
        "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",
      )
    for (fen in fensToTest) {
      testOnGame(FenParser.read(fen))
    }
  }

  @Test
  fun testInvalidFenFormat() {
    val invalidFens =
      listOf(
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR", // Missing parts
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq", // Missing moves
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1", // Invalid player
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - -1 1", // Negative half-move
      )
    for (fen in invalidFens) {
      try {
        FenParser.read(fen)
        throw AssertionError("Expected an exception for invalid FEN: $fen")
      } catch (_: IllegalArgumentException) {
        // Expected exception
      }
    }
  }

  @Test
  fun testEdgeCases() {
    val edgeCaseFens =
      listOf(
        "8/8/8/8/8/8/8/8 w - - 0 1", // Empty board
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1", // Black to move
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1", // No castling rights
      )
    for (fen in edgeCaseFens) {
      val game = FenParser.read(fen)
      assertEquals(fen, FenParser.parse(game))
    }
  }

  @Test
  fun testEnPassantParsing() {
    val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
    val game = FenParser.read(fen)
    assertEquals(4, game.position.enPassantColumn)
    assertEquals(fen, FenParser.parse(game))
  }

  companion object {
    fun testOnGame(game: Game) {
      assertEquals(game.position, FenParser.read(FenParser.parse(game)).position)
    }
  }
}
