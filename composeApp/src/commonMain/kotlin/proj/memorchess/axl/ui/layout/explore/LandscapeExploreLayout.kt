package proj.memorchess.axl.ui.layout.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Height below which the layout uses phone landscape paddings and a narrower side panel. */
private val COMPACT_HEIGHT_THRESHOLD = 480.dp

/**
 * Landscape explore layout: moves trail, board and control bar in a left column, with
 * [ExploreLayoutContent.sideInfo] in a fixed width right rail.
 */
@Composable
fun LandscapeExploreLayout(modifier: Modifier = Modifier, content: ExploreLayoutContent) {
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val compact = maxHeight < COMPACT_HEIGHT_THRESHOLD
    val pad = if (compact) 12.dp else 28.dp
    val sideWidth = if (compact) 280.dp else 420.dp
    val spacing = if (compact) 6.dp else 8.dp

    Row(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier.weight(1f).fillMaxHeight().padding(pad),
        verticalArrangement = Arrangement.spacedBy(spacing),
      ) {
        content.movesTrail(Modifier.fillMaxWidth())
        Row(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
        ) {
          content.board(Modifier.fillMaxHeight())
        }
        content.controlBar(Modifier.fillMaxWidth())
      }
      content.sideInfo(
        Modifier.width(sideWidth).fillMaxHeight().padding(vertical = pad, horizontal = 0.dp)
      )
    }
  }
}
