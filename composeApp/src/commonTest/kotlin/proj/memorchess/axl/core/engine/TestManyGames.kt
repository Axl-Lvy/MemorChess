package proj.memorchess.axl.core.engine

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import proj.memorchess.axl.core.engine.board.TestFenParser
import proj.memorchess.axl.test_util.getGames

class TestManyGames {

  @BeforeTest
  fun setup() {
    Logger.setMinSeverity(severity = Severity.Info)
  }

  @AfterTest
  fun teardown() {
    Logger.setMinSeverity(severity = Severity.Info)
  }

  @Test
  fun testManyGames() {
    val pgns = getGames()
    for (pgn in pgns) {
      val game = Game()
      for (move in pgn) {
        game.playMove(move)
        TestFenParser.testOnGame(game)
      }
    }
  }
}
