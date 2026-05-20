package proj.memorchess.axl.ui.components.explore

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import proj.memorchess.axl.core.data.explorer.ExplorerSource
import proj.memorchess.axl.core.data.explorer.ExplorerState
import proj.memorchess.axl.core.data.explorer.LichessExplorerMove
import proj.memorchess.axl.core.data.explorer.LichessExplorerResponse
import proj.memorchess.axl.core.data.explorer.LichessOpening

/** Renders [LichessExplorerPanelContent] in each [ExplorerState] and asserts the visible text. */
@OptIn(ExperimentalTestApi::class)
class TestLichessExplorerPanel {

  private val loadedResponse =
    LichessExplorerResponse(
      white = 10,
      draws = 5,
      black = 3,
      moves =
        listOf(
          LichessExplorerMove(uci = "e2e4", san = "e4", white = 7, draws = 2, black = 1),
          LichessExplorerMove(uci = "d2d4", san = "d4", white = 3, draws = 3, black = 2),
        ),
      opening = LichessOpening(eco = "B00", name = "King's Pawn"),
    )

  @Test
  fun idleStateShowsHint() = runComposeUiTest {
    setContent {
      LichessExplorerPanelContent(
        state = ExplorerState.Idle,
        source = ExplorerSource.MASTERS,
        onSetSource = {},
        onClickMove = {},
      )
    }
    onNode(hasText("Make a move to load popular replies.")).assertExists()
  }

  @Test
  fun loadingStateShowsHeader() = runComposeUiTest {
    setContent {
      LichessExplorerPanelContent(
        state = ExplorerState.Loading(fen = "f", source = ExplorerSource.LICHESS),
        source = ExplorerSource.LICHESS,
        onSetSource = {},
        onClickMove = {},
      )
    }
    onNode(hasText("Lichess explorer")).assertExists()
  }

  @Test
  fun loadedStateRendersOpeningAndMoves() = runComposeUiTest {
    setContent {
      LichessExplorerPanelContent(
        state =
          ExplorerState.Loaded(
            source = ExplorerSource.MASTERS,
            response = loadedResponse,
            isStale = false,
          ),
        source = ExplorerSource.MASTERS,
        onSetSource = {},
        onClickMove = {},
      )
    }
    onNode(hasText("King's Pawn")).assertExists()
    onNode(hasText("ECO B00")).assertExists()
    onAllNodes(hasTestTag("lichess_explorer_move_row:e4")).assertCountEquals(1)
    onAllNodes(hasTestTag("lichess_explorer_move_row:d4")).assertCountEquals(1)
  }

  @Test
  fun loadedEmptyStateShowsPlaceholder() = runComposeUiTest {
    setContent {
      LichessExplorerPanelContent(
        state =
          ExplorerState.Loaded(
            source = ExplorerSource.MASTERS,
            response = LichessExplorerResponse(0, 0, 0, emptyList(), opening = null),
            isStale = false,
          ),
        source = ExplorerSource.MASTERS,
        onSetSource = {},
        onClickMove = {},
      )
    }
    onNode(hasText("No games found for this position.")).assertExists()
  }

  @Test
  fun rateLimitedStateShowsMessage() = runComposeUiTest {
    setContent {
      LichessExplorerPanelContent(
        state = ExplorerState.RateLimited(source = ExplorerSource.MASTERS),
        source = ExplorerSource.MASTERS,
        onSetSource = {},
        onClickMove = {},
      )
    }
    onNode(hasText("Lichess rate limited the request. Try again in a moment.")).assertExists()
  }

  @Test
  fun errorStateShowsMessage() = runComposeUiTest {
    setContent {
      LichessExplorerPanelContent(
        state = ExplorerState.Error(source = ExplorerSource.MASTERS, message = "boom"),
        source = ExplorerSource.MASTERS,
        onSetSource = {},
        onClickMove = {},
      )
    }
    onNode(hasText("Could not load explorer: boom")).assertExists()
  }

  @Test
  fun clickingMoveRowCallsOnClickMoveWithSan() = runComposeUiTest {
    var clicked: String? = null
    setContent {
      LichessExplorerPanelContent(
        state =
          ExplorerState.Loaded(
            source = ExplorerSource.MASTERS,
            response = loadedResponse,
            isStale = false,
          ),
        source = ExplorerSource.MASTERS,
        onSetSource = {},
        onClickMove = { clicked = it },
      )
    }
    onAllNodes(hasTestTag("lichess_explorer_move_row:e4"))[0].performClick()
    kotlin.test.assertEquals("e4", clicked)
  }

  @Test
  fun clickingSourceChipCallsOnSetSource() = runComposeUiTest {
    var selected: ExplorerSource? = null
    setContent {
      LichessExplorerPanelContent(
        state = ExplorerState.Idle,
        source = ExplorerSource.MASTERS,
        onSetSource = { selected = it },
        onClickMove = {},
      )
    }
    onAllNodes(hasText("Lichess"))[0].performClick()
    kotlin.test.assertEquals(ExplorerSource.LICHESS, selected)
  }
}
