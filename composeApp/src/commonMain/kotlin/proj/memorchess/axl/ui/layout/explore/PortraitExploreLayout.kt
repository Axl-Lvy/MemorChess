package proj.memorchess.axl.ui.layout.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
fun PortraitExploreLayout(modifier: Modifier = Modifier, content: ExploreLayoutContent) {
  BoxWithConstraints {
    Column(
      verticalArrangement = Arrangement.spacedBy(if (this.maxHeight > 700.dp) 12.dp else 2.dp),
      modifier = modifier.fillMaxSize().padding(2.dp),
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
      if (this@BoxWithConstraints.maxHeight > 650.dp) {
        ExploreStateIndicators(stateIndicators = content.stateIndicators)
      }

      // Main board section with responsive sizing
      ExploreBoardSection(board = content.board)

      // Next moves section with horizontal scrolling
      ExploreNextMovesSection(nextMoveButtons = content.nextMoveButtons)

      // Action buttons section (save/delete)
      ExploreActionButtons(saveButton = content.saveButton, deleteButton = content.deleteButton)
    }
  }
}

@HotPreview(widthDp = 411, heightDp = 891, density = 2.625f)
@Composable
private fun PortraitExploreLayoutPreview() {
  PortraitExploreLayout(content = previewExploreLayoutContent)
}
