package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import proj.memorchess.axl.core.interactions.InteractionsManager

@Composable
fun Board(
  inverted: Boolean = false,
  interactionsManager: InteractionsManager,
  modifier: Modifier = Modifier,
) {
  val state = remember(inverted) { BoardGridState(inverted, interactionsManager) }

  Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
    BoardGrid(state = state, modifier = Modifier.fillMaxSize())

    if (interactionsManager.needPromotion.value) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PromotionSelector(state)
      }
    }
  }
}
