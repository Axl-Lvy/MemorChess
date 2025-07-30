package proj.memorchess.axl.ui.layout.training

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class TrainingLayoutContent(
  // INDICATORS
  var movesToTrain: @Composable (Modifier) -> Unit,
  var daysInAdvance: @Composable (Modifier) -> Unit,
  var successIndicator: @Composable (Modifier) -> Unit,

  // BOARD
  var board: @Composable (Modifier) -> Unit,
)
