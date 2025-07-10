package proj.memorchess.axl.core.engine.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import proj.memorchess.axl.core.engine.Game

class TestCache {
  @Test
  fun testCacheManyPosition() {
    val cache = mutableSetOf<proj.memorchess.axl.core.data.PositionIdentifier>()
    repeat(10) {
      val game = Game()
      cache.add(game.position.createIdentifier())
      game.playMove("e4")
      cache.add(game.position.createIdentifier())
      game.playMove("e5")
      cache.add(game.position.createIdentifier())
      game.playMove("Nf3")
      cache.add(game.position.createIdentifier())
    }
    assertEquals(4, cache.size)
  }
}
