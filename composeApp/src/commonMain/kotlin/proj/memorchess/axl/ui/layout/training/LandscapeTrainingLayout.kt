package proj.memorchess.axl.ui.layout.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LandscapeTrainingLayout(modifier: Modifier = Modifier, content: TrainingLayoutContent) {
  Row(
    modifier = modifier.fillMaxSize().padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    // Board Section - Takes up most of the space
    Box(modifier = Modifier.weight(3f).fillMaxHeight()) {
      content.board(Modifier.fillMaxSize().aspectRatio(1f, true))
    }

    // Side Panel with Information Cards
    Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Top) {
      Box(modifier = Modifier.weight(2f)) {
        Column {
          // Moves to train at the top
          content.movesToTrain(Modifier.fillMaxWidth())
          Spacer(modifier = Modifier.height(16.dp))

          // Days in advance in the middle
          content.daysInAdvance(Modifier.fillMaxWidth())
        }
      }
      Box(modifier = Modifier.weight(1f)) {
        // Success indicator at the bottom
        content.successIndicator(Modifier.fillMaxWidth())
      }
    }
  }
}
