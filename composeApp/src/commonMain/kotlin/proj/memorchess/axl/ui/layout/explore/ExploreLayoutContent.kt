package proj.memorchess.axl.ui.layout.explore

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Contains every component of the explore layout. */
class ExploreLayoutContent(
  // BOARD ACTIONS
  var resetButton: @Composable (Modifier) -> Unit,
  var reverseButton: @Composable (Modifier) -> Unit,
  var backButton: @Composable (Modifier) -> Unit,
  var forwardButton: @Composable (Modifier) -> Unit,

  /**
   * Composable supplier of next move buttons. The supplier pattern is used to allow the next move
   * buttons to be recomposed when the next moves change.
   */
  var nextMoveButtons: @Composable () -> List<@Composable () -> Unit>,

  // INDICATORS
  var stateIndicators: @Composable (Modifier) -> Unit,
  var playerTurnIndicator: @Composable (Modifier) -> Unit,

  // DATABASE ACTIONS
  var saveButton: @Composable (Modifier) -> Unit,
  var deleteButton: @Composable (Modifier) -> Unit,

  // BOARD
  var board: @Composable (Modifier) -> Unit,
)
