package proj.memorchess.axl.core.interaction

import kotlin.test.Test
import org.koin.core.component.inject
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.test_util.getGames

/** JVM only tests for [LinesExplorer] that are too slow for wasmJs. */
class TestLinesExplorerJvm : TestWithKoin() {
  private lateinit var interactionsManager: LinesExplorer
  private val treeStore: TreeStore by inject()

  override suspend fun setUp() {
    treeStore.eraseAll()
    interactionsManager = LinesExplorer(treeStore = treeStore)
  }

  @Test
  fun testManyGames() = test {
    val gameList = getGames().shuffled().take(10)
    gameList.forEach { game ->
      treeStore.eraseAll()
      interactionsManager = LinesExplorer(treeStore = treeStore)
      val refEngine = GameEngine()
      game.forEach {
        interactionsManager.playMove(it)
        refEngine.playSanMove(it)
        kotlin.test.assertEquals(refEngine.toFen(), interactionsManager.engine.toFen())
      }
    }
  }
}
