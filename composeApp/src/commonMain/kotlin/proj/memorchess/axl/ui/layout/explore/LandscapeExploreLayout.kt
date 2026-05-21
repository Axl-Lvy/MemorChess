package proj.memorchess.axl.ui.layout.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Desktop / landscape Kinetic explore layout. Composes the moves trail, board (with optional eval
 * rail) and control bar in a tall left column; the [ExploreLayoutContent.sideInfo] slot fills a
 * fixed-width right rail.
 */
@Composable
fun LandscapeExploreLayout(modifier: Modifier = Modifier, content: ExploreLayoutContent) {
  Row(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier.weight(1f).fillMaxHeight().padding(28.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      content.movesTrail(Modifier.fillMaxWidth())
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
      ) {
        content.board(Modifier.fillMaxHeight())
      }
      Spacer(modifier = Modifier.height(8.dp))
      content.controlBar(Modifier.fillMaxWidth())
    }
    content.sideInfo(
      Modifier.width(420.dp).fillMaxHeight().padding(vertical = 28.dp, horizontal = 0.dp)
    )
  }
}
