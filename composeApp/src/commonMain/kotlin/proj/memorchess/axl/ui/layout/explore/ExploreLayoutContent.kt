package proj.memorchess.axl.ui.layout.explore

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Contains every component of the explore layout. */
class ExploreLayoutContent {
  // BOARD ACTIONS
  var resetButton: @Composable (Modifier) -> Unit = {}
  var reverseButton: @Composable (Modifier) -> Unit = {}
  var backButton: @Composable (Modifier) -> Unit = {}
  var forwardButton: @Composable (Modifier) -> Unit = {}
  var nextMoves: List<@Composable () -> Unit> = emptyList()

  // INDICATORS
  var stateIndicators: @Composable (Modifier) -> Unit = {}
  var playerTurnIndicator: @Composable (Modifier) -> Unit = {}

  // DATABASE ACTIONS
  var saveButton: @Composable (Modifier) -> Unit = {}
  var deleteButton: @Composable (Modifier) -> Unit = {}

  // BOARD
  var board: @Composable (Modifier) -> Unit = {}
}
