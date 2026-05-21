package proj.memorchess.axl.ui.layout.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Mobile / portrait Kinetic explore layout. Stacks stat badges, moves trail, board (with thin eval
 * rail), control bar, and mobile info tabs vertically.
 */
@Composable
fun PortraitExploreLayout(modifier: Modifier = Modifier, content: ExploreLayoutContent) {
  Column(
    modifier = modifier.fillMaxSize().padding(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    content.statBadges(Modifier.fillMaxWidth())
    content.movesTrail(Modifier.fillMaxWidth())
    content.board(Modifier.fillMaxWidth())
    content.controlBar(Modifier.fillMaxWidth())
    content.mobileInfo(Modifier.fillMaxWidth().weight(1f))
  }
}
