package proj.memorchess.axl.ui.layout.explore

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Contains every component of the explore layout. */
class ExploreLayoutContent(
  var resetButton: @Composable (Modifier) -> Unit,
  var reverseButton: @Composable (Modifier) -> Unit,
  var backButton: @Composable (Modifier) -> Unit,
  var forwardButton: @Composable (Modifier) -> Unit,

  /** Supplies the next move buttons, so they recompose when the next moves change. */
  var nextMoveButtons: @Composable () -> List<@Composable () -> Unit>,
  var stateIndicators: @Composable (Modifier) -> Unit,
  var playerTurnIndicator: @Composable (Modifier) -> Unit,

  /** Horizontal evaluation bar rendered below the state indicators, or empty when disabled. */
  var evaluationPanel: @Composable (Modifier) -> Unit = {},

  /** Toggle button that enables/disables the evaluation bar. */
  var evaluationBarToggle: @Composable (Modifier) -> Unit = {},
  var saveButton: @Composable (Modifier) -> Unit,
  var deleteButton: @Composable (Modifier) -> Unit,
  var board: @Composable (Modifier) -> Unit,

  /** Horizontal moves trail rendered above the board. */
  var movesTrail: @Composable (Modifier) -> Unit = {},

  /** Row of board and save/delete controls. */
  var controlBar: @Composable (Modifier) -> Unit = {},

  /** Desktop right rail: next moves grid, Lichess panel, notes. */
  var sideInfo: @Composable (Modifier) -> Unit = {},

  /** Mobile bottom section: tabbed lines, Lichess and stats. */
  var mobileInfo: @Composable (Modifier) -> Unit = {},

  /** Stat badges row shown on mobile above the moves trail. */
  var statBadges: @Composable (Modifier) -> Unit = {},

  /** Text shown as the board shell corner tag (e.g. `"EXPLORE · italian/quiet"`). */
  var cornerTagText: String? = null,
)
