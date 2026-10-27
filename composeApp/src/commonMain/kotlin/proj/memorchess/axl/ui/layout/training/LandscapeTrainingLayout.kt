package proj.memorchess.axl.ui.layout.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Landscape training layout: the portrait single centered column with looser padding. */
@Composable
fun LandscapeTrainingLayout(modifier: Modifier = Modifier, content: TrainingLayoutContent) {
  Column(
    modifier =
      modifier.fillMaxSize().widthIn(max = 1100.dp).padding(horizontal = 28.dp, vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    content.counters(Modifier.fillMaxWidth())
    content.progress(Modifier.fillMaxWidth())
    content.movesTrail(Modifier.fillMaxWidth())
    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
      content.board(Modifier.fillMaxSize())
    }
    content.controlBar(Modifier.fillMaxWidth())
  }
}
