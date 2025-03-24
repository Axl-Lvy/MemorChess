package proj.ankichess.axl.pages

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import proj.ankichess.axl.board.Board

@Composable
fun Training() {
  var inverted by remember { mutableStateOf(false) }
  Column(
    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Board(inverted, modifier = Modifier.fillMaxWidth())
    Row(
      horizontalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxWidth().fillMaxHeight(),
    ) {
      Button(onClick = { inverted = !inverted }) {
        Icon(Icons.Rounded.Refresh, contentDescription = "Inverted")
      }
    }
  }
}
