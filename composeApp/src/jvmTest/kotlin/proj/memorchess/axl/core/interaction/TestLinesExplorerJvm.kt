package proj.memorchess.axl.core.interaction

import kotlin.test.Test
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.test_util.getGames

/** JVM-only tests for [LinesExplorer] that are too slow for wasmJs. */
class TestLinesExplorerJvm : TestWithKoin() {
  private lateinit var interactionsManager: LinesExplorer
  private val nodeManager: NodeManager by inject()
  private val database: DatabaseQueryManager by inject()

  override suspend fun setUp() {
    database.deleteAll(null)
    nodeManager.resetCacheFromSource()
    interactionsManager = LinesExplorer(nodeManager = nodeManager)
  }

  @Test
  fun testManyGames() = test {
    val gameList = getGames().shuffled().take(10)
    gameList.forEach { game ->
      database.deleteAll(null)
      nodeManager.resetCacheFromSource()
      interactionsManager = LinesExplorer(nodeManager = nodeManager)
      val refEngine = GameEngine()
      game.forEach {
        interactionsManager.playMove(it)
        refEngine.playSanMove(it)
        kotlin.test.assertEquals(refEngine.toFen(), interactionsManager.engine.toFen())
      }
    }
  }
}
