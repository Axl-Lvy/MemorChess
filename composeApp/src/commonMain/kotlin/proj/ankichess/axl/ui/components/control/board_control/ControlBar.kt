package proj.ankichess.axl.ui.components.control.board_control

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ControlBar(
  onReverseClick: () -> Unit,
  onResetClick: () -> Unit,
  onBackClick: () -> Unit,
  onForwardClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = modifier.fillMaxWidth()) {
    ControlButton.REVERSE.render(onReverseClick, modifier = Modifier.size(50.dp))
    ControlButton.RESET.render(onResetClick, modifier = Modifier.size(50.dp))
    ControlButton.BACK.render(onBackClick, modifier = Modifier.size(50.dp))
    ControlButton.FORWARD.render(onForwardClick, modifier = Modifier.size(50.dp))
  }
}
