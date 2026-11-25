package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import proj.memorchess.axl.ui.setKineticContent

/** Checks that [OutcomeBar] draws one segment per non-zero outcome and never a zero-width one. */
@OptIn(ExperimentalTestApi::class)
class TestOutcomeBar {

  private suspend fun ComposeUiTest.renderBar(white: Long, draws: Long, black: Long) {
    setKineticContent {
      OutcomeBar(
        white = white,
        draws = draws,
        black = black,
        modifier = Modifier.width(200.dp).height(8.dp),
      )
    }
  }

  private fun ComposeUiTest.assertSegments(white: Int, draws: Int, black: Int) {
    onAllNodes(hasTestTag("outcome_bar_white"), useUnmergedTree = true).assertCountEquals(white)
    onAllNodes(hasTestTag("outcome_bar_draws"), useUnmergedTree = true).assertCountEquals(draws)
    onAllNodes(hasTestTag("outcome_bar_black"), useUnmergedTree = true).assertCountEquals(black)
  }

  @Test
  fun zeroGamesRendersEmptyTrack() = runComposeUiTest {
    renderBar(0, 0, 0)

    assertSegments(white = 0, draws = 0, black = 0)
  }

  @Test
  fun onlyWhiteRendersOneSegment() = runComposeUiTest {
    renderBar(5, 0, 0)

    assertSegments(white = 1, draws = 0, black = 0)
  }

  @Test
  fun onlyBlackRendersOneSegment() = runComposeUiTest {
    renderBar(0, 0, 5)

    assertSegments(white = 0, draws = 0, black = 1)
  }

  @Test
  fun allOutcomesRenderThreeSegments() = runComposeUiTest {
    renderBar(7, 2, 1)

    assertSegments(white = 1, draws = 1, black = 1)
  }
}
