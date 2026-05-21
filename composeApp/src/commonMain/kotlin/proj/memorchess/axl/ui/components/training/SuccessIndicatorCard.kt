package proj.memorchess.axl.ui.components.training

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import proj.memorchess.axl.core.data.PositionKey

/**
 * @deprecated Superseded by the Kinetic Training layout. The Kinetic design folds success/failure
 *   feedback into the [TrainingCounterRow] counters and the inline board state — no dedicated card
 *   is rendered.
 *
 * Kept as an empty stub to preserve git history and to avoid stale imports breaking compilation if
 * something still references it. Will be deleted in a follow-up pass.
 */
@Deprecated(
  "Superseded by the Kinetic Training layout; feedback is folded into counters + board state.",
  level = DeprecationLevel.WARNING,
)
@Composable
fun SuccessIndicatorCard(
  @Suppress("UNUSED_PARAMETER") isCorrect: Boolean,
  @Suppress("UNUSED_PARAMETER") isVisible: Boolean,
  @Suppress("UNUSED_PARAMETER") nextMove: () -> Unit,
  @Suppress("UNUSED_PARAMETER") failedPosition: PositionKey?,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier)
}
