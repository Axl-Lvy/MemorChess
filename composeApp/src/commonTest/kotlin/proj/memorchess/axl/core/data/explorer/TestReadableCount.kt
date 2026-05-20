package proj.memorchess.axl.core.data.explorer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import proj.memorchess.axl.ui.components.explore.LichessExplorerPanelContent

/**
 * Renders the panel with rows whose game counts hit each `toReadableCount` formatting branch.
 *
 * Lives at this seam (not as a unit test of `toReadableCount` itself, which is private) so we still
 * pin the boundary behaviour without exposing the helper.
 */
@OptIn(ExperimentalTestApi::class)
class TestReadableCount {

  @Test
  fun zeroAndBoundaryGameCountsRenderTheirExpectedString() = runComposeUiTest {
    val response =
      LichessExplorerResponse(
        white = 0,
        draws = 0,
        black = 0,
        moves =
          listOf(
            LichessExplorerMove(uci = "a2a3", san = "a3", white = 0, draws = 0, black = 0),
            LichessExplorerMove(uci = "h2h4", san = "h4", white = 1, draws = 0, black = 0),
            LichessExplorerMove(uci = "e2e4", san = "e4", white = 999, draws = 0, black = 0),
            LichessExplorerMove(uci = "d2d4", san = "d4", white = 1000, draws = 0, black = 0),
            LichessExplorerMove(uci = "g1f3", san = "Nf3", white = 1_000_000, draws = 0, black = 0),
          ),
        opening = null,
      )
    setContent {
      LichessExplorerPanelContent(
        state =
          ExplorerState.Loaded(
            source = ExplorerSource.MASTERS,
            response = response,
            isStale = false,
          ),
        source = ExplorerSource.MASTERS,
        onSetSource = {},
        onClickMove = {},
      )
    }
    onNode(hasText("0")).assertExists()
    onNode(hasText("1")).assertExists()
    onNode(hasText("999")).assertExists()
    onNode(hasText("1k")).assertExists()
    onNode(hasText("1M")).assertExists()
  }
}
