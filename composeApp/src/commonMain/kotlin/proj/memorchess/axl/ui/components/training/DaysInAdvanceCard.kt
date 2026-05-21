package proj.memorchess.axl.ui.components.training

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * @deprecated Superseded by the Kinetic Training layout. The training page no longer surfaces a
 *   dedicated "days in advance" card — the value is implicit in the empty-session screen.
 *
 * Kept as an empty stub to preserve git history and to avoid stale imports breaking compilation if
 * something still references it. Will be deleted in a follow-up pass.
 */
@Deprecated(
  "Superseded by the Kinetic Training layout; no longer rendered.",
  level = DeprecationLevel.WARNING,
)
@Composable
fun DaysInAdvanceCard(@Suppress("UNUSED_PARAMETER") days: Int, modifier: Modifier = Modifier) {
  Box(modifier = modifier)
}
