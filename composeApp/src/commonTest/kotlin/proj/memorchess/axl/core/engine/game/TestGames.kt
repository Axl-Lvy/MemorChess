package proj.memorchess.axl.core.engine.game

import kotlin.test.Test
import proj.memorchess.axl.test_util.getGames

class TestGames {
  @Test
  fun createPgns() {
    val pgns = getGames()
    print(pgns)
  }
}
