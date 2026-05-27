package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
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
import proj.memorchess.axl.core.data.explorer.ExplorerViewModel
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.engine.evaluation.EvaluationScore
import proj.memorchess.axl.core.engine.evaluation.StockfishEvaluator
import proj.memorchess.axl.core.graph.NodeState
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.ui.components.board.BestMoveArrowData
import proj.memorchess.axl.ui.components.board.Board
import proj.memorchess.axl.ui.components.board.EvaluationBar
import proj.memorchess.axl.ui.components.board.Piece
import proj.memorchess.axl.ui.components.board.StateIndicator
import proj.memorchess.axl.ui.components.buttons.ControlButton
import proj.memorchess.axl.ui.components.explore.ExploreBoardSection
import proj.memorchess.axl.ui.components.explore.ExploreCtrlBar
import proj.memorchess.axl.ui.components.explore.ExploreCtrlBarActions
import proj.memorchess.axl.ui.components.explore.ExploreEvalState
import proj.memorchess.axl.ui.components.explore.ExploreSidebar
import proj.memorchess.axl.ui.components.explore.ExploreStatBadgesRow
import proj.memorchess.axl.ui.components.explore.MoveDisplay
import proj.memorchess.axl.ui.components.explore.MovesTrail
import proj.memorchess.axl.ui.layout.explore.ExploreLayoutContent
import proj.memorchess.axl.ui.layout.explore.LandscapeExploreLayout
import proj.memorchess.axl.ui.layout.explore.PortraitExploreLayout
import proj.memorchess.axl.ui.theme.LocalKineticPalette

/**
 * Shared explorer content relying on a [LinesExplorer].
 *
 * @param explorer The explorer instance.
 * @param explorerViewModel View model driving the Lichess opening explorer panels.
 * @param onSave Invoked when the Kinetic control bar Save button is tapped.
 * @param onDelete Invoked when the Kinetic control bar Delete button is tapped.
 * @param header Optional header content rendered above the layout (legacy).
 */
@Composable
fun ExplorerContent(
  explorer: LinesExplorer,
  explorerViewModel: ExplorerViewModel,
  onSave: () -> Unit,
  onDelete: () -> Unit,
  header: @Composable () -> Unit = {},
) {
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

  // v1 trail: track the last-played SAN as a single chip. The explorer's NavigationHistory is
  // protected so we cannot derive the full played-moves list here without touching core/. We keep
  // a local list that grows with each new playMove and reset alongside the explorer.
  val playedMoves = remember { mutableStateListOf<MoveDisplay>() }
  var playerTurnWhite by remember { mutableStateOf(explorer.engine.playerTurn == Player.WHITE) }

  // Read the live save-state of the current position. `LinesExplorer.state` is
  // mutableStateOf-backed
  // so this reactively recomposes when the user navigates, saves, or deletes.
  val palette = LocalKineticPalette.current
  val nodeState = explorer.state
  val cornerTagLabel = nodeStateLabel(nodeState)
  val cornerTagColor = nodeStateColor(nodeState, palette)

  DisposableEffect(Unit) { onDispose { evaluator?.close() } }

  LaunchedEffect(evaluator) {
    evaluator?.evaluate(explorer.engine.toFen().value, explorer.engine.playerTurn == Player.BLACK)
  }

  remember {
    explorer.registerCallBack {
      nextMoves.clear()
      nextMoves.addAll(explorer.getNextMoves())
      playerTurnWhite = explorer.engine.playerTurn == Player.WHITE
      evaluator?.let { eval ->
        coroutineScope.launch {
          eval.evaluate(explorer.engine.toFen().value, explorer.engine.playerTurn == Player.BLACK)
        }
      }
    }
  }

  val content =
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
      saveButton = { _ -> },
      deleteButton = { _ -> },
      board = { boardModifier ->
        ExploreBoardSection(
          modifier = boardModifier,
          board = { innerModifier ->
            Board(
              inverted = inverted,
              interactionsManager = explorer,
              bestMoveArrow = bestMoveArrow,
              modifier = innerModifier,
            )
          },
          cornerTagText = cornerTagLabel,
          cornerTagColor = cornerTagColor,
          compact = false,
          eval =
            ExploreEvalState(
              enabled = evalBarEnabled,
              ratio = computeEvalRatio(evaluation),
              displayValue = evaluation?.let { formatEvaluationScore(it) },
              thin = false,
            ),
        )
      },
      movesTrail = { trailModifier ->
        MovesTrail(
          moves = playedMoves,
          currentIndex = playedMoves.lastIndex,
          onSeek = {},
          modifier = trailModifier,
          openingName = null,
          pgnText = null,
        )
      },
      controlBar = { ctrlModifier ->
        ExploreCtrlBar(
          modifier = ctrlModifier,
          actions =
            ExploreCtrlBarActions(
              onReset = { explorer.reset() },
              onReverse = { inverted = !inverted },
              onBack = { explorer.back() },
              onForward = { explorer.forward() },
              onToggleEval = {
                val next = !evalBarEnabled
                evalBarEnabled = next
                EVAL_BAR_ENABLED_SETTING.setValue(next)
                evaluator = updateEvaluator(next, evaluator, maxDepth)
              },
              onSave = onSave,
              onDelete = onDelete,
            ),
          evalEnabled = evalBarEnabled,
          playerTurnWhite = playerTurnWhite,
        )
      },
      sideInfo = { sideModifier ->
        ExploreSidebar(
          modifier = sideModifier,
          nextMoves = nextMoves.toList(),
          onPlayMove = { san -> coroutineScope.launch { explorer.playMove(san) } },
          explorerViewModel = explorerViewModel,
          onPlayLichessMove = { san -> coroutineScope.launch { explorer.playMove(san) } },
        )
      },
      mobileInfo = { infoModifier ->
        ExploreSidebar(
          modifier = infoModifier,
          nextMoves = nextMoves.toList(),
          onPlayMove = { san -> coroutineScope.launch { explorer.playMove(san) } },
          explorerViewModel = explorerViewModel,
          onPlayLichessMove = { san -> coroutineScope.launch { explorer.playMove(san) } },
        )
      },
      statBadges = { badgeModifier -> ExploreStatBadgesRow(modifier = badgeModifier) },
      cornerTagText = cornerTagLabel,
    )

  header()

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    if (maxHeight > maxWidth) PortraitExploreLayout(Modifier.fillMaxSize(), content)
    else LandscapeExploreLayout(Modifier.fillMaxSize(), content)
  }
}

/**
 * Maps a [NodeState] to the short uppercase label shown in the board shell's corner tag. Mirrors
 * the wording of the legacy [proj.memorchess.axl.ui.components.board.StateIndicator] but compressed
 * to fit in the small mono tag: SAVED / PARTIAL / NEW / UNKNOWN MOVE / BAD.
 */
private fun nodeStateLabel(state: NodeState): String =
  when (state) {
    NodeState.FIRST -> "START"
    NodeState.SAVED_GOOD -> "SAVED"
    NodeState.SAVED_BAD -> "PARTIAL"
    NodeState.SAVED_GOOD_BUT_UNKNOWN_MOVE -> "SAVED · NEW MOVE"
    NodeState.SAVED_BAD_BUT_UNKNOWN_MOVE -> "PARTIAL · NEW MOVE"
    NodeState.UNKNOWN -> "NOT SAVED"
    NodeState.BAD_STATE -> "CONFLICT"
  }

/**
 * Maps a [NodeState] to the color used to tint the corner-tag label. Green = position is part of a
 * fully-saved good line; accent (orange) = saved but only partially good; ink3 = unknown / new
 * position; red = conflicting (the same position is reached by both a good and a bad line).
 */
private fun nodeStateColor(
  state: NodeState,
  palette: proj.memorchess.axl.ui.theme.KineticPalette,
): Color =
  when (state) {
    NodeState.FIRST,
    NodeState.SAVED_GOOD -> palette.green
    NodeState.SAVED_BAD,
    NodeState.SAVED_GOOD_BUT_UNKNOWN_MOVE,
    NodeState.SAVED_BAD_BUT_UNKNOWN_MOVE -> palette.accentText
    NodeState.UNKNOWN -> palette.ink3
    NodeState.BAD_STATE -> palette.red
  }

/**
 * Approximate eval ratio from an [EvaluationScore]. Returns `0.5f` when null. Maps centipawns into
 * `[0f, 1f]` by clamping into `[-600, +600]` centipawns; mates collapse to fully-favorable for the
 * side delivering mate.
 */
private fun computeEvalRatio(score: EvaluationScore?): Float =
  when (score) {
    null -> 0.5f
    is EvaluationScore.Centipawns -> {
      val clamped = score.value.coerceIn(-600, 600).toFloat()
      0.5f + clamped / 1200f
    }
    is EvaluationScore.Mate -> if (score.moves >= 0) 1f else 0f
  }

/**
 * Pretty-prints an [EvaluationScore] for the eval rail readout. `Centipawns(32)` becomes `"+0.3"`,
 * `Mate(3)` becomes `"M3"`, `Mate(-2)` becomes `"-M2"`.
 */
private fun formatEvaluationScore(score: EvaluationScore): String =
  when (score) {
    is EvaluationScore.Centipawns -> {
      val pawns = score.value / 100f
      val sign = if (score.value >= 0) "+" else "-"
      val absPawns = if (pawns >= 0f) pawns else -pawns
      val whole = absPawns.toInt()
      val tenth = ((absPawns - whole) * 10f).toInt().coerceIn(0, 9)
      "$sign$whole.$tenth"
    }
    is EvaluationScore.Mate -> if (score.moves >= 0) "M${score.moves}" else "-M${-score.moves}"
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
