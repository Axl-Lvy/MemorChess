package proj.memorchess.axl.ui.components.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import proj.memorchess.axl.core.engine.pieces.vectors.Bishop
import proj.memorchess.axl.core.engine.pieces.vectors.Knight
import proj.memorchess.axl.core.engine.pieces.vectors.Queen
import proj.memorchess.axl.core.engine.pieces.vectors.Rook

@Composable
fun PromotionSelector(state: BoardGridState) {
  val scope = rememberCoroutineScope()
  val player = state.interactionsManager.game.position.playerTurn
  val possibilities = listOf(Queen(player), Rook(player), Bishop(player), Knight(player))

  Row(
    modifier =
      Modifier.clip(RoundedCornerShape(24.dp))
        .background(Color.Black.copy(alpha = 0.7f))
        .padding(16.dp)
  ) {
    possibilities.forEach { piece ->
      val label = "Promote to ${piece.toString().lowercase()}"

      Box(
        modifier =
          Modifier.size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.85f))
            .padding(8.dp)
            .clickable(
              onClick = { scope.launch { state.applyPromotion(piece) } },
              onClickLabel = label,
            )
      ) {
        Piece(piece, Modifier.fillMaxSize())
      }

      Spacer(modifier = Modifier.width(12.dp))
    }
  }
}
