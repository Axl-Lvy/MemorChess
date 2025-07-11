package proj.memorchess.axl.ui.components.board.control

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.description_board_next_move
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.ui.components.buttons.WideScrollBarChild
import proj.memorchess.axl.ui.components.buttons.WideScrollableRow

@Composable
fun NextMoveBar(
  moveList: List<String>,
  playMove: suspend (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val coroutineScope = rememberCoroutineScope()
  Box(modifier = modifier.fillMaxWidth()) {
    if (moveList.isEmpty()) {
      WideScrollableRow(
        modifier = modifier.fillMaxWidth(),
        children = listOf(WideScrollBarChild("No next move", {}, { Text("No next move") })),
        changeSelectedColor = false,
      )
    } else {
      val children =
        moveList.map {
          WideScrollBarChild(
            stringResource(Res.string.description_board_next_move, it),
            { coroutineScope.launch { playMove(it) } },
            { _ -> Text(it) },
          )
        }
      WideScrollableRow(
        modifier = modifier.fillMaxWidth(),
        children = children,
        minWidth = 64.dp,
        changeSelectedColor = false,
      )
    }
  }
}
