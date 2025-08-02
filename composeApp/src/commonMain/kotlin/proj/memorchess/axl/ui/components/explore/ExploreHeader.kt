package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ExploreHeader(
  modifier: Modifier = Modifier,
  reverseButton: @Composable (Modifier) -> Unit,
  resetButton: @Composable (Modifier) -> Unit,
  playerTurnIndicator: @Composable (Modifier) -> Unit,
  backButton: @Composable (Modifier) -> Unit,
  forwardButton: @Composable (Modifier) -> Unit,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Row(
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
      reverseButton(Modifier.weight(1f))
      Spacer(modifier = Modifier.width(6.dp))
      resetButton(Modifier.weight(1f))
      Spacer(modifier = Modifier.width(6.dp))
      playerTurnIndicator(Modifier.weight(1.2f))
      Spacer(modifier = Modifier.width(6.dp))
      backButton(Modifier.weight(1f))
      Spacer(modifier = Modifier.width(6.dp))
      forwardButton(Modifier.weight(1f))
    }
  }
}
