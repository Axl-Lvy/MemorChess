package proj.ankichess.axl.ui.components.control.board_control

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import proj.ankichess.axl.ui.components.board.Board

@Composable
fun ControllableBoard(modifier: Modifier = Modifier) {
  var inverted by remember { mutableStateOf(false) }
  var reloadKey by remember { mutableStateOf(false) }
  Column(
    //    modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
  ) {
    ControlBar(
      modifier = Modifier.height(50.dp),
      onReverseClick = { inverted = !inverted },
      onResetClick = { reloadKey = !reloadKey },
    )
    Board(inverted, reloadKey, modifier = Modifier.fillMaxWidth())
  }
}
