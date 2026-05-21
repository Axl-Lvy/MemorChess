package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import proj.memorchess.axl.ui.components.board.KineticBoardShell
import proj.memorchess.axl.ui.components.board.KineticEvalRail

/**
 * Wraps the chess board inside a [KineticBoardShell] and optionally renders a [KineticEvalRail] to
 * the left.
 *
 * @param board Composable that paints the board; receives a `Modifier` sized to the inner area.
 * @param cornerTagText Optional text shown as the shell corner tag.
 * @param cornerTagColor Optional tint applied to the corner tag text. Used to surface the node save
 *   state (e.g. green when saved, red when bad).
 * @param compact When `true`, uses the compact board shell (tighter padding/shadow).
 * @param evalEnabled When `true`, an eval rail is rendered to the left of the board.
 * @param evalRatio Position of the eval marker (`0f` = all black, `1f` = all white).
 * @param evalDisplayValue Optional text rendered beneath the rail (e.g. `"+0.4"`).
 * @param railThin When `true`, the eval rail is rendered in its thin variant.
 */
@Composable
fun ExploreBoardSection(
  modifier: Modifier = Modifier,
  board: @Composable (Modifier) -> Unit,
  cornerTagText: String? = null,
  cornerTagColor: Color? = null,
  compact: Boolean = false,
  evalEnabled: Boolean = false,
  evalRatio: Float = 0.5f,
  evalDisplayValue: String? = null,
  railThin: Boolean = false,
) {
  Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    BoxWithConstraints {
      val side = min(maxWidth, maxHeight)
      Row(
        modifier = Modifier.size(side),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (evalEnabled) {
          KineticEvalRail(
            whiteRatio = evalRatio,
            displayValue = evalDisplayValue,
            modifier = Modifier.fillMaxHeight(),
            thin = railThin,
          )
        }
        KineticBoardShell(
          modifier = Modifier.fillMaxHeight().aspectRatio(1f),
          compact = compact,
          cornerTag = cornerTagText,
          cornerTagColor = cornerTagColor,
        ) {
          board(Modifier.fillMaxWidth().aspectRatio(1f))
        }
      }
    }
  }
}
