package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.components.board.control.TrainingBoardPage
import proj.memorchess.axl.ui.pages.navigation.Route

@Composable
fun Training() {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .padding(horizontal = 2.dp, vertical = 8.dp)
        .testTag(Route.TrainingRoute.getLabel()),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    TrainingBoardPage(modifier = Modifier.fillMaxSize())
  }
}
