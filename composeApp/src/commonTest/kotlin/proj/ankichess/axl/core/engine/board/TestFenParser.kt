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

  companion object {
    fun testOnGame(game: Game) {
      assertEquals(game.position, FenParser.read(FenParser.parse(game)).position)
    }
  }
}
