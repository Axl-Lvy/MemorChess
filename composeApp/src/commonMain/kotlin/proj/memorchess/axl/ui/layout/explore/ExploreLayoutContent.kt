package proj.memorchess.axl.ui.layout.explore

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Contains every component of the explore layout.
 *
 * The Kinetic-era slots are: [movesTrail], [controlBar], [sideInfo], [mobileInfo], [statBadges],
 * and [cornerTagText]. The remaining legacy slots are kept to preserve backwards compatibility with
 * code that has not yet been ported.
 */
class ExploreLayoutContent(
  // BOARD ACTIONS (legacy — used by control bar internals)
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

  // EVALUATION
  /** Horizontal evaluation bar rendered below the state indicators, or empty when disabled. */
  var evaluationPanel: @Composable (Modifier) -> Unit = {},

  /** Toggle button that enables/disables the evaluation bar. */
  var evaluationBarToggle: @Composable (Modifier) -> Unit = {},

  // DATABASE ACTIONS
  var saveButton: @Composable (Modifier) -> Unit,
  var deleteButton: @Composable (Modifier) -> Unit,

  // BOARD
  var board: @Composable (Modifier) -> Unit,

  // KINETIC SLOTS
  /** Horizontal moves trail rendered above the board. */
  var movesTrail: @Composable (Modifier) -> Unit = {},

  /** Replacement for the loose reset / reverse / back / forward / save / delete button row. */
  var controlBar: @Composable (Modifier) -> Unit = {},

  /** Desktop right rail — next moves grid, Lichess panel, notes. */
  var sideInfo: @Composable (Modifier) -> Unit = {},

  /** Mobile bottom-section — tabbed lines / lichess / stats. */
  var mobileInfo: @Composable (Modifier) -> Unit = {},

  /** Small stat badges row used on mobile above the moves trail. */
  var statBadges: @Composable (Modifier) -> Unit = {},

  /** Text shown as the board shell corner tag (e.g. `"EXPLORE · italian/quiet"`). */
  var cornerTagText: String? = null,
)
