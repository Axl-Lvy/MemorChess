package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlin.math.ceil
import kotlin.test.Test
import proj.memorchess.axl.core.data.explorer.ExplorerSource
import proj.memorchess.axl.core.data.explorer.LichessExplorerResponse
import proj.memorchess.axl.core.data.explorer.PositionStatsState
import proj.memorchess.axl.ui.setKineticContent

/** Guards the stats bar's footprint at the usable width of the narrowest info panel. */
@OptIn(ExperimentalTestApi::class)
class TestPositionStatsBarWidth {

  private val widest =
    PositionStatsState.Shown(
      source = ExplorerSource.LICHESS,
      response = LichessExplorerResponse(999_900_000, 1, 1, moves = emptyList()),
      isCurrent = true,
    )

  private suspend fun ComposeUiTest.renderAtRailWidth(fontScale: Float) {
    setKineticContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        Box(modifier = Modifier.width(256.dp)) { PositionStatsBarContent(state = widest) }
      }
    }
  }

  /** Whether the percentages are drawn narrower than their natural one-line width. */
  private fun ComposeUiTest.percentsOverflow(): Boolean {
    val node =
      onAllNodes(hasText("100 · <1 · <1"), useUnmergedTree = true).fetchSemanticsNodes().single()
    val layouts = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    val naturalWidth = ceil(layouts.single().multiParagraph.intrinsics.maxIntrinsicWidth).toInt()
    return node.size.width < naturalWidth
  }

  @Test
  fun widestTextFitsHeightBudget() = runComposeUiTest {
    renderAtRailWidth(fontScale = 1f)

    val bar = onNodeWithTag("position_stats_bar").fetchSemanticsNode()
    with(density) { bar.size.height.toDp() shouldBeLessThanOrEqualTo 28.dp }
    percentsOverflow() shouldBe false
  }

  @Test
  fun percentagesStayWholeAtLargeFontScale() = runComposeUiTest {
    renderAtRailWidth(fontScale = 2f)

    percentsOverflow() shouldBe false
  }
}
