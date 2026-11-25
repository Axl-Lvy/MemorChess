package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import kotlin.test.Test
import proj.memorchess.axl.core.data.explorer.ExplorerSource
import proj.memorchess.axl.core.data.explorer.LichessExplorerResponse
import proj.memorchess.axl.core.data.explorer.PositionStatsState
import proj.memorchess.axl.ui.setKineticContent

/** Guards the stats bar's footprint at the usable width of the narrowest info panel. */
@OptIn(ExperimentalTestApi::class)
class TestPositionStatsBarWidth {

  @Test
  fun widestTextKeepsBarVisibleWithinHeightBudget() = runComposeUiTest {
    val state =
      PositionStatsState.Shown(
        source = ExplorerSource.LICHESS,
        response = LichessExplorerResponse(999_900_000, 1, 1, moves = emptyList()),
        isCurrent = true,
      )
    setKineticContent {
      Box(modifier = Modifier.width(256.dp)) { PositionStatsBarContent(state = state) }
    }

    val bar = onNodeWithTag("position_stats_bar").fetchSemanticsNode()
    val white =
      onAllNodes(hasTestTag("outcome_bar_white"), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .single()
    with(density) {
      bar.size.height.toDp() shouldBeLessThanOrEqualTo 28.dp
      white.size.width.toDp() shouldBeGreaterThanOrEqualTo 40.dp
    }
  }
}
