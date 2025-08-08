package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.min

@Composable
fun ExploreBoardSection(modifier: Modifier = Modifier, board: @Composable (Modifier) -> Unit) {
  Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    BoxWithConstraints {
      val boardSize = min(maxWidth, maxHeight)
      board(Modifier.size(boardSize).aspectRatio(1f))
    }
  }
}
