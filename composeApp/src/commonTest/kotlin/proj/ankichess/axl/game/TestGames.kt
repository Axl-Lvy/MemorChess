package proj.ankichess.axl.game

import getGames
import kotlin.test.Test

class TestGames {
  @Test
  fun createPgns() {
    val pgns = getGames()
    print(pgns)
  }
}
