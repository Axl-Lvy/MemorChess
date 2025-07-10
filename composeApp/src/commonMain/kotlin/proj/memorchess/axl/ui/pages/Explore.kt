package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.ui.components.board.control.ControllableBoard
import proj.memorchess.axl.ui.pages.navigation.Destination

@Composable
fun Explore() {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .padding(horizontal = 2.dp, vertical = 8.dp)
        .testTag(Destination.EXPLORE.name),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    ControllableBoard(modifier = Modifier.fillMaxWidth())
  }
}
