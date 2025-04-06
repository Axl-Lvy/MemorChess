package proj.ankichess.axl.core.engine

import com.diamondedge.logging.KmLogging
import com.diamondedge.logging.LogLevel
import kotlin.test.Test
import proj.ankichess.axl.core.engine.board.TestFenParser
import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.game.getGames

class TestManyGames {
  @Test
  fun testManyGames() {
    KmLogging.setLogLevel(LogLevel.Off)
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
