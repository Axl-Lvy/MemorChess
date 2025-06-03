package proj.ankichess.axl.core.engine.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import proj.ankichess.axl.core.data.PositionKey
import proj.ankichess.axl.core.engine.Game

class TestCache {
  @Test
  fun testCacheManyPosition() {
    val cache = mutableSetOf<proj.ankichess.axl.core.data.PositionKey>()
    repeat(10) {
      val game = Game()
      cache.add(game.position.toImmutablePosition())
      game.playMove("e4")
      cache.add(game.position.toImmutablePosition())
      game.playMove("e5")
      cache.add(game.position.toImmutablePosition())
      game.playMove("Nf3")
      cache.add(game.position.toImmutablePosition())
    }
    assertEquals(4, cache.size)
  }
}
