package proj.ankichess.axl.ui.components.control.board_control

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import proj.ankichess.axl.core.interactions.InteractionManager
import proj.ankichess.axl.ui.components.board.Board
import proj.ankichess.axl.ui.util.impl.BasicReloader

@Composable
fun ControllableBoard(modifier: Modifier = Modifier) {
  var inverted by remember { mutableStateOf(false) }
  val interactionManagerReloader = remember { BasicReloader() }
  val interactionManager = remember(interactionManagerReloader.getKey()) { InteractionManager() }
  Column(verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
    ControlBar(
      modifier = Modifier.height(50.dp),
      onReverseClick = { inverted = !inverted },
      onResetClick = { interactionManagerReloader.reload() },
    )
    Board(
      inverted,
      interactionManager,
      interactionManagerReloader,
      modifier = modifier.fillMaxWidth(),
    )
  }
}
