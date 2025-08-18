package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ExploreBoardTopping(
  modifier: Modifier = Modifier,
  boardTopping: @Composable (Modifier) -> Unit,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    boardTopping(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp, vertical = 8.dp))
  }
}
