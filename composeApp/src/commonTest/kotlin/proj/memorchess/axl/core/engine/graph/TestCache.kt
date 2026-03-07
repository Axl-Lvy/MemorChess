package proj.memorchess.axl.core.engine.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import proj.memorchess.axl.core.engine.GameEngine

class TestCache {
  @Test
  fun testCacheManyPosition() {
    val cache = mutableSetOf<proj.memorchess.axl.core.data.PositionKey>()
    repeat(10) {
      val engine = GameEngine()
      cache.add(engine.toPositionKey())
      engine.playSanMove("e4")
      cache.add(engine.toPositionKey())
      engine.playSanMove("e5")
      cache.add(engine.toPositionKey())
      engine.playSanMove("Nf3")
      cache.add(engine.toPositionKey())
    }
    assertEquals(4, cache.size)
  }
}
