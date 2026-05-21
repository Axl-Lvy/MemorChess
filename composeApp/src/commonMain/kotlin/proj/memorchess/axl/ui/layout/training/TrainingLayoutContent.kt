package proj.memorchess.axl.ui.layout.training

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Slots filled by [proj.memorchess.axl.ui.components.board.control.TrainingBoardPage] and consumed
 * by the Kinetic Training layouts ([PortraitTrainingLayout], [LandscapeTrainingLayout]).
 *
 * The Kinetic training design is deliberately minimal: a 3-counter row at the top, a thin progress
 * rail, an optional moves trail, the board, and a control bar at the bottom.
 *
 * @property board Renders the board shell + chess board. Receives a [Modifier] sized to its slot.
 * @property counters Renders the 3-counter row (SUCCESS / FAIL / LEFT).
 * @property progress Renders the [proj.memorchess.axl.ui.components.training.KineticProgressRail].
 * @property movesTrail Renders the moves trail (may be empty for v1).
 * @property controlBar Renders the bottom control bar (SKIP / turn pill / HINT / REVEAL).
 * @property cornerTagText Optional label to apply on the board shell corner (e.g. "TRAINING").
 */
data class TrainingLayoutContent(
  var board: @Composable (Modifier) -> Unit,
  var counters: @Composable (Modifier) -> Unit,
  var progress: @Composable (Modifier) -> Unit,
  var movesTrail: @Composable (Modifier) -> Unit,
  var controlBar: @Composable (Modifier) -> Unit,
  var cornerTagText: String? = null,
)
