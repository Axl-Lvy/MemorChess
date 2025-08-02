package proj.memorchess.axl.ui.layout.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PortraitTrainingLayout(modifier: Modifier = Modifier, content: TrainingLayoutContent) {
  Column(
    modifier = modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Box(modifier = Modifier.weight(1f)) {
      // Top row with info cards
      Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        content.movesToTrain(Modifier.weight(1f))
        content.daysInAdvance(Modifier.weight(1f))
      }
    }
    content.board(Modifier.fillMaxSize().weight(2f))

    Box(modifier = Modifier.weight(1f)) {

      // Success indicator at the bottom
      content.successIndicator(Modifier.fillMaxSize())
    }
  }
}
