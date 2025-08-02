package proj.memorchess.axl.ui.layout.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.drick.compose.hotpreview.HotPreview
import proj.memorchess.axl.ui.components.explore.ExploreActionButtons
import proj.memorchess.axl.ui.components.explore.ExploreBoardSection
import proj.memorchess.axl.ui.components.explore.ExploreHeader
import proj.memorchess.axl.ui.components.explore.ExploreNextMovesSection
import proj.memorchess.axl.ui.components.explore.ExploreStateIndicators
import proj.memorchess.axl.ui.util.previewExploreLayoutContent

@Composable
fun LandscapeExploreLayout(modifier: Modifier = Modifier, content: ExploreLayoutContent) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.fillMaxSize().padding(16.dp),
  ) {
    // Left side: Board section (takes up most space but constrained by ratio)
    ExploreBoardSection(
      board = content.board,
      modifier =
        Modifier.fillMaxHeight() // Use 90% of available height to determine max size
          .aspectRatio(1f)
          .weight(2f, fill = false),
    )

    // Right side: All controls in a scrollable column with ratio-based constraint
    Column(
      verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
      modifier =
        Modifier.weight(1f, fill = false)
          .fillMaxHeight()
          .aspectRatio(1f)
          .verticalScroll(rememberScrollState()),
    ) {
      // Header with control buttons and player turn indicator
      ExploreHeader(
        reverseButton = content.reverseButton,
        resetButton = content.resetButton,
        playerTurnIndicator = content.playerTurnIndicator,
        backButton = content.backButton,
        forwardButton = content.forwardButton,
      )

      // State indicators section
      ExploreStateIndicators(stateIndicators = content.stateIndicators)

      // Next moves section with horizontal scrolling
      ExploreNextMovesSection(nextMoveButtons = content.nextMoveButtons)

      // Action buttons section (save/delete)
      ExploreActionButtons(saveButton = content.saveButton, deleteButton = content.deleteButton)
    }
  }
}

@HotPreview(widthDp = 5500, heightDp = 1100, density = 2.625f)
@Composable
private fun LandscapeExploreLayoutPreview() {
  LandscapeExploreLayout(content = previewExploreLayoutContent)
}
