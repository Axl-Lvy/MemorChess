package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import proj.memorchess.axl.core.interactions.InteractionsManager

/**
 * Chess board with optional best-move arrow overlay.
 *
 * @param inverted Whether the board is shown from Black's perspective.
 * @param interactionsManager Handles piece interactions and move validation.
 * @param bestMoveArrow Arrow overlay data, or `null` to hide the arrow.
 * @param modifier Modifier for the board.
 */
@Composable
fun Board(
  inverted: Boolean = false,
  interactionsManager: InteractionsManager,
  bestMoveArrow: BestMoveArrowData? = null,
  modifier: Modifier = Modifier,
) {
  val state = remember(inverted) { BoardGridState(inverted, interactionsManager) }

  Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
    BoardGrid(state = state, bestMoveArrow = bestMoveArrow, modifier = Modifier.fillMaxSize())

    if (interactionsManager.needPromotion.value) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PromotionSelector(state)
      }
    }
  }
}
