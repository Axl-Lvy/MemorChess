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
import proj.memorchess.axl.ui.components.board.KineticBoardShell
import proj.memorchess.axl.ui.components.board.registeredFlash

/**
 * Wraps the chess board inside a [KineticBoardShell] for the Training page.
 *
 * Sizing strategy mirrors `ExploreBoardSection`: a [BoxWithConstraints] picks the smaller of the
 * available width/height so the shell stays square regardless of slot shape, then the inner [Board]
 * receives a `Modifier.fillMaxWidth().aspectRatio(1f)`.
 *
 * @param inverted Whether to render the board from the black perspective.
 * @param trainer Active [InteractionsManager] handling clicks on the board.
 * @param modifier Modifier applied to the outer container; typically `Modifier.fillMaxWidth()`.
 * @param compact When `true`, uses the compact [KineticBoardShell] variant (tighter padding /
 *   shadow). Callers pass `true` in portrait, `false` in landscape.
 * @param cornerTagText Optional text rendered as the shell corner tag (e.g. `"TRAINING"`).
 * @param attempt Monotonic counter of graded moves; a change pulses the [registeredFlash] border.
 * @param success Whether the move that produced the current [attempt] was correct.
 */
@Composable
fun BoardContainer(
  inverted: Boolean,
  trainer: InteractionsManager,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  cornerTagText: String? = null,
  attempt: Int = 0,
  success: Boolean = true,
) {
  Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    BoxWithConstraints {
      val side = min(maxWidth, maxHeight)
      Box(
        modifier = Modifier.size(side).registeredFlash(attempt = attempt, success = success),
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
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
          )
        }
      }
    }
  }
}
