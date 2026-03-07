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
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.core.engine.PieceKind

@Composable
fun PromotionSelector(state: BoardGridState) {
  val scope = rememberCoroutineScope()
  val player = state.interactionsManager.engine.playerTurn
  val possibilities =
    listOf(
      PieceKind.QUEEN to ChessPiece(PieceKind.QUEEN, player),
      PieceKind.ROOK to ChessPiece(PieceKind.ROOK, player),
      PieceKind.BISHOP to ChessPiece(PieceKind.BISHOP, player),
      PieceKind.KNIGHT to ChessPiece(PieceKind.KNIGHT, player),
    )

  Row(
    modifier =
      Modifier.clip(RoundedCornerShape(24.dp))
        .background(Color.Black.copy(alpha = 0.7f))
        .padding(16.dp)
  ) {
    possibilities.forEachIndexed { index, (kind, piece) ->
      val label = "Promote to ${kind.name.lowercase()}"

      Box(
        modifier =
          Modifier.size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.85f))
            .padding(8.dp)
            .clickable(
              onClick = { scope.launch { state.applyPromotion(kind) } },
              onClickLabel = label,
            )
      ) {
        Piece(piece, Modifier.fillMaxSize())
      }

      if (index < possibilities.lastIndex) {
        Spacer(modifier = Modifier.width(12.dp))
      }
    }
  }
}
