package proj.memorchess.axl.ui.layout.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.drick.compose.hotpreview.HotPreview
import proj.memorchess.axl.ui.util.previewExploreLayoutContent

@Composable
fun LandscapeExploreLayout(modifier: Modifier = Modifier, content: ExploreLayoutContent) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(10.dp, alignment = Alignment.CenterHorizontally),
    modifier = modifier.fillMaxWidth(),
  ) {
    Box(modifier = Modifier.weight(1f)) {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp, alignment = Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize(),
      ) {
        Box(modifier = Modifier.weight(0.5f).fillMaxWidth(), contentAlignment = Alignment.Center) {
          content.playerTurnIndicator(Modifier.fillMaxHeight())
        }
        Box(
          modifier = Modifier.weight(0.5f).widthIn(min = 24.dp),
          contentAlignment = Alignment.Center,
        ) {
          content.stateIndicators(Modifier.fillMaxHeight())
        }
        Box(modifier = Modifier.weight(1f)) {
          Row(modifier = Modifier.fillMaxSize()) {
            content.resetButton(Modifier.weight(1f))
            content.reverseButton(Modifier.weight(1f))
          }
        }
        Box(modifier = Modifier.weight(1f)) {
          Row(modifier = Modifier.fillMaxSize()) {
            content.backButton(Modifier.weight(1f))
            content.forwardButton(Modifier.weight(1f))
          }
        }
        Box(modifier = Modifier.weight(1f)) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth().heightIn(max = 48.dp),
          ) {
            content.saveButton(Modifier.weight(1f).fillMaxWidth())
            content.deleteButton(Modifier.weight(1f).fillMaxWidth())
          }
        }
      }
    }
    BoxWithConstraints {
      if (maxHeight > maxWidth) content.board(Modifier.fillMaxWidth())
      else content.board(Modifier.fillMaxHeight())
    }
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
      VerticalDoubleScrollableRow(
        children = content.nextMoveButtons(),
        modifier = Modifier.fillMaxHeight().widthIn(max = 100.dp),
      )
    }
  }
}

@HotPreview(widthDp = 1920, heightDp = 1080, captionBar = true)
@Composable
private fun LandscapeExploreLayoutPreview() {
  LandscapeExploreLayout(content = previewExploreLayoutContent)
}
