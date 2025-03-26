package proj.ankichess.axl.pages

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.RefreshCw
import compose.icons.feathericons.Repeat
import proj.ankichess.axl.board.Board

@Composable
fun Training() {
  var inverted by remember { mutableStateOf(false) }
  var reloadKey by remember { mutableStateOf(false) }
  Column(
    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Board(inverted, reloadKey, modifier = Modifier.fillMaxWidth())
    Row(
      horizontalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxWidth().fillMaxHeight(),
    ) {
      Button(onClick = { inverted = !inverted }) {
        Icon(imageVector = FeatherIcons.Repeat, contentDescription = "Inverted")
      }
      Button(onClick = { reloadKey = !reloadKey }) {
        Icon(imageVector = FeatherIcons.RefreshCw, contentDescription = "Refresh")
      }
    }
  }
}
