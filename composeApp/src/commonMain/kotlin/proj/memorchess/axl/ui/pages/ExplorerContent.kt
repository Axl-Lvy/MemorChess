package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import compose.icons.FeatherIcons
import compose.icons.feathericons.BarChart2
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.description_board_next_move
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.config.BEST_MOVE_ARROW_ENABLED_SETTING
import proj.memorchess.axl.core.config.ENGINE_MAX_DEPTH_SETTING
import proj.memorchess.axl.core.config.EVAL_BAR_ENABLED_SETTING
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.engine.evaluation.StockfishEvaluator
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.ui.components.board.BestMoveArrowData
import proj.memorchess.axl.ui.components.board.Board
import proj.memorchess.axl.ui.components.board.EvaluationBar
import proj.memorchess.axl.ui.components.board.Piece
import proj.memorchess.axl.ui.components.board.StateIndicator
import proj.memorchess.axl.ui.components.buttons.ControlButton
import proj.memorchess.axl.ui.layout.explore.ExploreLayoutContent
import proj.memorchess.axl.ui.layout.explore.LandscapeExploreLayout
import proj.memorchess.axl.ui.layout.explore.PortraitExploreLayout

/**
 * Shared explorer content relying on a [LinesExplorer].
 *
 * @param explorer The explorer instance (LinesExplorer or BookExplorer).
 * @param saveButton Custom composable for the save button (can be null if not applicable).
 * @param deleteButton Custom composable for the delete button (can be null if not applicable).
 * @param header Optional header content to display above the explorer.
 */
@Composable
fun ExplorerContent(
  explorer: LinesExplorer,
  saveButton: @Composable (Modifier) -> Unit,
  deleteButton: @Composable (Modifier) -> Unit,
  header: @Composable () -> Unit = {},
) {
  val modifier = Modifier.fillMaxWidth()
  var inverted by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()
  val nextMoves = remember { mutableStateListOf(*explorer.getNextMoves().toTypedArray()) }
  var evalBarEnabled by remember { mutableStateOf(EVAL_BAR_ENABLED_SETTING.getValue()) }
  val bestMoveArrowEnabled by remember {
    mutableStateOf(BEST_MOVE_ARROW_ENABLED_SETTING.getValue())
  }

  // Evaluator is created/destroyed based on the toggles so the device stops computing when off.
  val needsEngine = evalBarEnabled || bestMoveArrowEnabled
  val maxDepth = remember { ENGINE_MAX_DEPTH_SETTING.getValue() }
  var evaluator by remember {
    mutableStateOf(if (needsEngine) StockfishEvaluator(maxDepth) else null)
  }
  val evaluation by (evaluator?.evaluation ?: flowOf(null)).collectAsState(initial = null)
  val currentDepth by (evaluator?.currentDepth ?: flowOf(null)).collectAsState(initial = null)
  val bestMove by (evaluator?.bestMove ?: flowOf(null)).collectAsState(initial = null)
  val bestMoveArrow by remember {
    derivedStateOf {
      val finalBestMove = bestMove
      if (bestMoveArrowEnabled && finalBestMove != null) {
        BestMoveArrowData(
          finalBestMove,
          explorer.engine.pieceAt(finalBestMove.from.row, finalBestMove.from.col)?.kind,
        )
      } else null
    }
  }

  DisposableEffect(Unit) { onDispose { evaluator?.close() } }

  LaunchedEffect(evaluator) {
    evaluator?.evaluate(explorer.engine.toFen().value, explorer.engine.playerTurn == Player.BLACK)
  }

  remember {
    explorer.registerCallBack {
      nextMoves.clear()
      nextMoves.addAll(explorer.getNextMoves())
      evaluator?.let { eval ->
        coroutineScope.launch {
          eval.evaluate(explorer.engine.toFen().value, explorer.engine.playerTurn == Player.BLACK)
        }
      }
    }
  }

  val content = remember {
    ExploreLayoutContent(
      resetButton = { ControlButton.RESET.render(it) { explorer.reset() } },
      reverseButton = { ControlButton.REVERSE.render(it) { inverted = !inverted } },
      backButton = { ControlButton.BACK.render(it) { explorer.back() } },
      forwardButton = { ControlButton.FORWARD.render(it) { explorer.forward() } },
      nextMoveButtons = {
        nextMoves.map<String, @Composable (() -> Unit)> {
          { NextMoveButton(it) { coroutineScope.launch { explorer.playMove(it) } } }
        }
      },
      playerTurnIndicator = { PlayerTurnIndicator(explorer) },
      stateIndicators = { StateIndicator(it, explorer.state) },
      evaluationPanel = {
        if (evalBarEnabled) {
          EvaluationBar(evaluation = evaluation, currentDepth = currentDepth, modifier = it)
        }
      },
      evaluationBarToggle = {
        EvalBarToggleButton(evalBarEnabled, it) { enabled ->
          evalBarEnabled = enabled
          EVAL_BAR_ENABLED_SETTING.setValue(enabled)
          evaluator = updateEvaluator(enabled, evaluator, maxDepth)
        }
      },
      saveButton = saveButton,
      deleteButton = deleteButton,
      board = {
        Board(
          inverted = inverted,
          interactionsManager = explorer,
          bestMoveArrow = bestMoveArrow,
          modifier = it,
        )
      },
    )
  }

  header()

  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    if (maxHeight > maxWidth) PortraitExploreLayout(modifier, content)
    else LandscapeExploreLayout(modifier, content)
  }
}

@Composable
private fun PlayerTurnIndicator(explorer: LinesExplorer) {
  var playerTurn by remember { mutableStateOf(explorer.engine.playerTurn == Player.WHITE) }
  explorer.registerCallBack { playerTurn = explorer.engine.playerTurn == Player.WHITE }
  Piece(
    if (playerTurn) ChessPiece(PieceKind.KING, Player.WHITE)
    else ChessPiece(PieceKind.KING, Player.BLACK)
  )
}

@Composable
private fun EvalBarToggleButton(
  checked: Boolean,
  modifier: Modifier,
  onCheckedChange: (Boolean) -> Unit,
) {
  FilledIconToggleButton(
    checked = checked,
    onCheckedChange = onCheckedChange,
    modifier = modifier,
  ) {
    Icon(imageVector = FeatherIcons.BarChart2, contentDescription = "Toggle evaluation bar")
  }
}

/**
 * Returns the evaluator to use after a toggle change: creates a new one when [shouldRun] is `true`
 * and none exists, or closes and returns `null` when [shouldRun] is `false`.
 */
private fun updateEvaluator(
  shouldRun: Boolean,
  current: StockfishEvaluator?,
  maxDepth: Int,
): StockfishEvaluator? =
  when {
    shouldRun && current == null -> StockfishEvaluator(maxDepth)
    !shouldRun -> {
      current?.close()
      null
    }
    else -> current
  }

@Composable
private fun NextMoveButton(move: String, playMove: () -> Unit) {
  Box(
    modifier =
      Modifier.fillMaxSize()
        .testTag(stringResource(Res.string.description_board_next_move, move))
        .clickable { playMove() },
    contentAlignment = Alignment.Center,
  ) {
    Text(move)
  }
}
