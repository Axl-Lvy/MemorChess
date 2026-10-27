package proj.memorchess.axl.ui.components.training

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.min
import proj.memorchess.axl.core.interactions.InteractionsManager
import proj.memorchess.axl.ui.components.board.Board
import proj.memorchess.axl.ui.components.board.BoardTrainingFeedback
import proj.memorchess.axl.ui.components.board.KineticBoardShell
import proj.memorchess.axl.ui.components.board.registeredFlash

/**
 * Training board inside a [KineticBoardShell], kept square at the smaller side of its slot.
 *
 * @param inverted whether the board is seen from black's side.
 */
@Composable
fun BoardContainer(
  inverted: Boolean,
  trainer: InteractionsManager,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  cornerTagText: String? = null,
  feedback: BoardTrainingFeedback = BoardTrainingFeedback(),
) {
  Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    BoxWithConstraints {
      val side = min(maxWidth, maxHeight)
      Box(
        modifier =
          Modifier.size(side)
            .registeredFlash(attempt = feedback.attempt, success = feedback.isCorrect == true),
        contentAlignment = Alignment.Center,
      ) {
        KineticBoardShell(
          modifier = Modifier.fillMaxHeight().aspectRatio(1f),
          compact = compact,
          cornerTag = cornerTagText,
        ) {
          Board(
            inverted = inverted,
            interactionsManager = trainer,
            feedback = feedback,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
          )
        }
      }
    }
  }
}
