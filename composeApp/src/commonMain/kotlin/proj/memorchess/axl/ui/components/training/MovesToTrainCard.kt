package proj.memorchess.axl.ui.components.training

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * @deprecated Superseded by [TrainingCounterRow] which shows the remaining count as the "LEFT"
 *   counter inside the Kinetic 3-counter row.
 *
 * Kept as an empty stub to preserve git history and to avoid stale imports breaking compilation if
 * something still references it. Will be deleted in a follow-up pass.
 */
@Deprecated(
  "Superseded by TrainingCounterRow; no longer rendered.",
  level = DeprecationLevel.WARNING,
)
@Composable
fun MovesToTrainCard(
  @Suppress("UNUSED_PARAMETER") numberOfMoves: Int,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier)
}
