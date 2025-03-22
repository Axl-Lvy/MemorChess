package proj.ankichess.axl.core.game

import com.diamondedge.logging.KmLogging
import com.diamondedge.logging.LogLevel
import getGames
import kotlin.test.Test

class TestManyGames {
  @Test
  fun testManyGames() {
    KmLogging.setLogLevel(LogLevel.Off)
    val pgns = getGames()
    print(pgns.size)
    for (pgn in pgns) {
      val game = Game()
      for (move in pgn) {
        game.playMove(move)
      }
    }
  }
}
