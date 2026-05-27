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
 * Eval-rail configuration for [ExploreBoardSection].
 *
 * @property enabled When `true`, an eval rail is rendered to the left of the board.
 * @property ratio Position of the eval marker (`0f` = all black, `1f` = all white).
 * @property displayValue Optional text rendered beneath the rail (e.g. `"+0.4"`).
 * @property thin When `true`, the eval rail is rendered in its thin variant.
 */
data class ExploreEvalState(
  val enabled: Boolean = false,
  val ratio: Float = 0.5f,
  val displayValue: String? = null,
  val thin: Boolean = false,
)

/**
 * Wraps the chess board inside a [KineticBoardShell] and optionally renders a [KineticEvalRail] to
 * the left.
 *
 * @param board Composable that paints the board; receives a `Modifier` sized to the inner area.
 * @param modifier External modifier applied to the outer box.
 * @param cornerTagText Optional text shown as the shell corner tag.
 * @param cornerTagColor Optional tint applied to the corner tag text. Used to surface the node save
 *   state (e.g. green when saved, red when bad).
 * @param compact When `true`, uses the compact board shell (tighter padding/shadow).
 * @param eval Eval-rail configuration.
 */
@Composable
fun ExploreBoardSection(
  board: @Composable (Modifier) -> Unit,
  modifier: Modifier = Modifier,
  cornerTagText: String? = null,
  cornerTagColor: Color? = null,
  compact: Boolean = false,
  eval: ExploreEvalState = ExploreEvalState(),
) {
  Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    BoxWithConstraints {
      val side = min(maxWidth, maxHeight)
      Row(
        modifier = Modifier.size(side),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (eval.enabled) {
          KineticEvalRail(
            whiteRatio = eval.ratio,
            displayValue = eval.displayValue,
            modifier = Modifier.fillMaxHeight(),
            thin = eval.thin,
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
