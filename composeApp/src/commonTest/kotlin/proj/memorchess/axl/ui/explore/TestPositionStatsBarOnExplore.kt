package proj.memorchess.axl.ui.explore

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import org.koin.core.component.inject
import proj.memorchess.axl.core.data.explorer.ExplorerSource
import proj.memorchess.axl.core.data.explorer.LichessExplorerResponse
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.test_util.InMemoryExplorerCache
import proj.memorchess.axl.test_util.TestWithKoin
import proj.memorchess.axl.ui.pages.Explore
import proj.memorchess.axl.ui.setTestContent
import proj.memorchess.axl.ui.waitUntilNodeExists

/** Checks that the Explore page shows the position stats bar for a cached position. */
@OptIn(ExperimentalTestApi::class)
class TestPositionStatsBarOnExplore : TestWithKoin() {

  private val explorerCache: InMemoryExplorerCache by inject()

  @Test
  fun explorePageShowsStatsForStartPosition() = runComposeUiTest {
    koinSetUp()
    try {
      explorerCache.seed(
        GameEngine().toFen(),
        ExplorerSource.MASTERS,
        LichessExplorerResponse(456_000, 372_000, 372_000, moves = emptyList()),
      )

      setTestContent { InitializeApp { Explore() } }

      waitUntilNodeExists(hasTestTag("position_stats_bar").and(hasText("MASTERS · 1.2M")))
    } finally {
      koinTearDown()
    }
  }
}
