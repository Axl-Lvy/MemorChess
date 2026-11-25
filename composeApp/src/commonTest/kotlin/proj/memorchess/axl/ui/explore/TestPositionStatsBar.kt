package proj.memorchess.axl.ui.explore

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import proj.memorchess.axl.core.data.explorer.ExplorerSource
import proj.memorchess.axl.core.data.explorer.LichessExplorerResponse
import proj.memorchess.axl.core.data.explorer.PositionStatsState
import proj.memorchess.axl.ui.components.explore.PositionStatsBarContent
import proj.memorchess.axl.ui.setKineticContent

/** Renders [PositionStatsBarContent] in each [PositionStatsState] and checks what it shows. */
@OptIn(ExperimentalTestApi::class)
class TestPositionStatsBar {

  private val tag = "position_stats_bar"

  private fun shown(
    white: Long,
    draws: Long,
    black: Long,
    source: ExplorerSource = ExplorerSource.MASTERS,
    isCurrent: Boolean = true,
  ) =
    PositionStatsState.Shown(
      source = source,
      response = LichessExplorerResponse(white, draws, black, moves = emptyList()),
      isCurrent = isCurrent,
    )

  private suspend fun ComposeUiTest.render(state: PositionStatsState) {
    setKineticContent { PositionStatsBarContent(state = state) }
  }

  private fun ComposeUiTest.assertBarHas(matcher: SemanticsMatcher) {
    onAllNodes(hasTestTag(tag).and(matcher)).assertCountEquals(1)
  }

  @Test
  fun hiddenEmitsNoNode() = runComposeUiTest {
    render(PositionStatsState.Hidden)

    onAllNodes(hasTestTag(tag)).assertCountEquals(0)
  }

  @Test
  fun currentMastersShowsLabelAndPercents() = runComposeUiTest {
    render(shown(456_000, 372_000, 372_000))

    assertBarHas(hasText("MASTERS · 1.2M"))
    assertBarHas(hasText("38 · 31 · 31"))
    onAllNodes(
        hasTestTag(tag).and(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)),
      )
      .assertCountEquals(0)
  }

  @Test
  fun lichessLabelNamesRatingBucket() = runComposeUiTest {
    render(shown(10, 5, 3, source = ExplorerSource.LICHESS))

    assertBarHas(hasText("LICHESS 2500+ · 18"))
  }

  @Test
  fun zeroGamesShowsDash() = runComposeUiTest {
    render(shown(0, 0, 0))

    assertBarHas(hasText("MASTERS · 0"))
    assertBarHas(hasText("—"))
    assertBarHas(hasContentDescription("Masters: no games"))
  }

  @Test
  fun tinyShareShowsLessThanOne() = runComposeUiTest {
    render(shown(1000, 0, 1))

    assertBarHas(hasText("100 · 0 · <1"))
  }

  @Test
  fun outdatedStatsAreMarkedUpdating() = runComposeUiTest {
    render(shown(38, 31, 31, isCurrent = false))

    assertBarHas(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Updating"))
  }

  @Test
  fun sameHeightWhetherCurrentOrNot() = runComposeUiTest {
    var state: PositionStatsState by mutableStateOf(shown(38, 31, 31))
    setKineticContent { PositionStatsBarContent(state = state) }
    val currentHeight = onNodeWithTag(tag).fetchSemanticsNode().size.height

    state = shown(38, 31, 31, isCurrent = false)
    waitForIdle()

    onNodeWithTag(tag).fetchSemanticsNode().size.height shouldBe currentHeight
  }

  @Test
  fun contentDescriptionReadsFullSentence() = runComposeUiTest {
    render(shown(38, 31, 31))

    assertBarHas(
      hasContentDescription(
        "Masters: 100 games, white wins 38%, draws 31%, black wins 31%",
      ),
    )
  }

  @Test
  fun contentDescriptionUsesSingularForOneGame() = runComposeUiTest {
    render(shown(1, 0, 0))

    assertBarHas(
      hasContentDescription("Masters: 1 game, white wins 100%, draws 0%, black wins 0%"),
    )
  }

  @Test
  fun contentDescriptionSpellsOutLargeCount() = runComposeUiTest {
    render(shown(456_000, 372_000, 372_000))

    assertBarHas(
      hasContentDescription(
        "Masters: 1200000 games, white wins 38%, draws 31%, black wins 31%",
      ),
    )
  }
}
