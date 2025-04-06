package proj.ankichess.axl.ui.components.control.board_control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun NextMoveBar(moveList: List<String>, playMove: (String) -> Unit, modifier: Modifier = Modifier) {
  Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = modifier.fillMaxWidth()) {
    moveList.forEach { move -> Button(onClick = { playMove(move) }) { Text(text = move) } }
  }
}
