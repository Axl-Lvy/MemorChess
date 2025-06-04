package proj.memorchess.axl.ui.components.control.board_control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.description_board_next_move
import org.jetbrains.compose.resources.stringResource

@Composable
fun NextMoveBar(moveList: List<String>, playMove: (String) -> Unit, modifier: Modifier = Modifier) {
  Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = modifier.fillMaxWidth()) {
    moveList.forEach { move ->
      Button(
        onClick = { playMove(move) },
        modifier = modifier.testTag(stringResource(Res.string.description_board_next_move, move)),
      ) {
        Text(text = move)
      }
    }
  }
}
