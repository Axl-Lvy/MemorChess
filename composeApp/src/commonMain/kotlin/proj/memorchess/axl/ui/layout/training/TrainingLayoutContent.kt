package proj.memorchess.axl.ui.layout.training

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Slots of the training layouts ([PortraitTrainingLayout], [LandscapeTrainingLayout]). Each slot
 * receives a [Modifier] sized to it.
 *
 * @property cornerTagText Optional label on the board shell corner (e.g. "TRAINING").
 */
data class TrainingLayoutContent(
  var board: @Composable (Modifier) -> Unit,
  var counters: @Composable (Modifier) -> Unit,
  var progress: @Composable (Modifier) -> Unit,
  var movesTrail: @Composable (Modifier) -> Unit,
  var controlBar: @Composable (Modifier) -> Unit,
  var cornerTagText: String? = null,
)
