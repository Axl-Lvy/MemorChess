package proj.ankichess.axl.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import proj.ankichess.axl.ui.components.control.board_control.ControllableBoardPage

@Composable
fun Explore() {
  Column(
    modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    ControllableBoardPage(modifier = Modifier.fillMaxWidth())
  }
}
