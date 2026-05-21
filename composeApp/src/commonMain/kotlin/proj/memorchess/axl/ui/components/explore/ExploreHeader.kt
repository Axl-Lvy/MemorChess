package proj.memorchess.axl.ui.components.explore

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Superseded by [ExploreCtrlBar] and the application top bar. Kept as a no-op stub so any remaining
 * call site continues to compile.
 */
@Deprecated(
  "Use ExploreCtrlBar (control bar) and KineticTopBar (chrome) instead.",
  level = DeprecationLevel.WARNING,
)
@Composable
fun ExploreHeader(
  modifier: Modifier = Modifier,
  reverseButton: @Composable (Modifier) -> Unit,
  resetButton: @Composable (Modifier) -> Unit,
  playerTurnIndicator: @Composable (Modifier) -> Unit,
  backButton: @Composable (Modifier) -> Unit,
  forwardButton: @Composable (Modifier) -> Unit,
  evaluationBarToggle: @Composable (Modifier) -> Unit = {},
) {
  // Intentionally empty: rendered by ExploreCtrlBar / KineticTopBar in the new design.
  val ignored =
    modifier to
      reverseButton to
      resetButton to
      playerTurnIndicator to
      backButton to
      forwardButton to
      evaluationBarToggle
  // Touch the references so the compiler doesn't warn about unused parameters.
  @Suppress("UNUSED_EXPRESSION") ignored
}
