package proj.memorchess.axl.ui.components.training

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.core.interactions.InteractionsManager
import proj.memorchess.axl.ui.components.board.Board

@Composable
fun BoardContainer(inverted: Boolean, trainer: InteractionsManager, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier.aspectRatio(1f),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
  ) {
    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) { Board(inverted, trainer) }
  }
}
