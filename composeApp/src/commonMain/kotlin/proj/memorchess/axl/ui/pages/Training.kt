package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.components.control.board_control.TrainingBoardPage
import proj.memorchess.axl.ui.pages.navigation.Destination

@Composable
fun Training() {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .padding(horizontal = 2.dp, vertical = 8.dp)
        .testTag(Destination.TRAINING.name),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    TrainingBoardPage(modifier = Modifier.fillMaxSize())
  }
}
