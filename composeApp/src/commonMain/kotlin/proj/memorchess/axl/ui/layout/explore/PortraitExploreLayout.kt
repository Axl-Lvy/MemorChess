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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.drick.compose.hotpreview.HotPreview
import proj.memorchess.axl.ui.util.previewExploreLayoutContent

@Composable
fun PortraitExploreLayout(modifier: Modifier = Modifier, content: ExploreLayoutContent) {
  Column(
    verticalArrangement = Arrangement.spacedBy(5.dp, alignment = Alignment.CenterVertically),
    modifier = modifier.fillMaxHeight(),
  ) {
    Box(modifier = Modifier.weight(1f)) {
      Column(verticalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxSize()) {
        Row(
          horizontalArrangement = Arrangement.SpaceEvenly,
          modifier = Modifier.fillMaxWidth().heightIn(max = 48.dp),
        ) {
          content.reverseButton(Modifier.fillMaxHeight())
          content.resetButton(Modifier.fillMaxHeight())
          content.playerTurnIndicator(Modifier.fillMaxHeight())
          content.backButton(Modifier.fillMaxHeight())
          content.forwardButton(Modifier.fillMaxHeight())
        }
        content.stateIndicators(Modifier.fillMaxWidth().heightIn(max = 48.dp))
      }
    }
    BoxWithConstraints {
      if (maxHeight > maxWidth) content.board(Modifier.fillMaxWidth())
      else content.board(Modifier.fillMaxHeight())
    }
    Box(modifier = Modifier.weight(1f)) {
      Column(verticalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxSize()) {
        HorizontalScrollableRow(
          children = content.nextMoveButtons(),
          modifier = Modifier.heightIn(max = 32.dp),
        )
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
}

@HotPreview(widthDp = 411, heightDp = 891, density = 2.625f)
@Composable
private fun PortraitExploreLayoutPreview() {
  PortraitExploreLayout(content = previewExploreLayoutContent)
}
