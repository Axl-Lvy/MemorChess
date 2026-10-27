package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateMapOf
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
import memorchess.composeapp.generated.resources.description_toggle_eval_bar
import memorchess.composeapp.generated.resources.tag_conflict
import memorchess.composeapp.generated.resources.tag_not_saved
import memorchess.composeapp.generated.resources.tag_partial
import memorchess.composeapp.generated.resources.tag_partial_new
import memorchess.composeapp.generated.resources.tag_saved
import memorchess.composeapp.generated.resources.tag_saved_new
import memorchess.composeapp.generated.resources.tag_start
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.config.BEST_MOVE_ARROW_ENABLED_SETTING
import proj.memorchess.axl.core.config.ENGINE_MAX_DEPTH_SETTING
import proj.memorchess.axl.core.config.EVAL_BAR_ENABLED_SETTING
import proj.memorchess.axl.core.data.explorer.CachedExplorer
import proj.memorchess.axl.core.data.explorer.ExplorerViewModel
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.engine.evaluation.EvaluationScore
import proj.memorchess.axl.core.engine.evaluation.StockfishEvaluator
import proj.memorchess.axl.core.graph.NodeState
import proj.memorchess.axl.core.graph.TreeStore
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
 * Read-only configuration for [ExplorerContent], hiding Save, Delete and the save-state indicators.
 *
 * @property initialInverted `true` opens the board from black's side.
 * @property cornerTag Replaces the save-state corner tag.
 */
data class ExplorerViewerMode(val initialInverted: Boolean = false, val cornerTag: String? = null)

/** Creates an [ExplorerViewModel] that follows [explorer]'s current position. */
@Composable
internal fun rememberExplorerViewModel(
  explorer: LinesExplorer,
  cachedExplorer: CachedExplorer,
): ExplorerViewModel {
  val coroutineScope = rememberCoroutineScope()
  val explorerViewModel =
    remember(cachedExplorer, coroutineScope) { ExplorerViewModel(cachedExplorer, coroutineScope) }
  LaunchedEffect(explorer) { explorerViewModel.setFen(explorer.engine.toFen()) }
  remember { explorer.registerCallBack { explorerViewModel.setFen(explorer.engine.toFen()) } }
  return explorerViewModel
}

/**
 * Explorer page content for [explorer]: board, moves trail, controls and sidebar.
 *
 * @param viewerMode When non-null, renders the read-only viewer.
 * @param myTreeStore When non-null, good moves the user holds in it are tagged YOURS in the
 *   sidebar.
 */
@Composable
fun ExplorerContent(
  explorer: LinesExplorer,
  explorerViewModel: ExplorerViewModel,
  onSave: () -> Unit,
  onDelete: () -> Unit,
  header: @Composable () -> Unit = {},
  viewerMode: ExplorerViewerMode? = null,
  myTreeStore: TreeStore? = null,
) {
  val readOnly = viewerMode != null
  var inverted by remember { mutableStateOf(viewerMode?.initialInverted ?: false) }
  val coroutineScope = rememberCoroutineScope()
  val nextMoves = remember { mutableStateListOf<String>() }
  val yoursBySan = remember { mutableStateMapOf<String, Boolean>() }
  val playedSans = remember { mutableStateListOf<String>() }

  LaunchedEffect(explorer) {
    nextMoves.clear()
    yoursBySan.clear()
    nextMoves.addAll(explorer.getNextMoves())
    refreshYours(nextMoves, myTreeStore, explorer, yoursBySan)
    playedSans.clear()
    playedSans.addAll(explorer.playedMoves())
  }
  var evalBarEnabled by remember { mutableStateOf(EVAL_BAR_ENABLED_SETTING.getValue()) }
  val bestMoveArrowEnabled by remember {
    mutableStateOf(BEST_MOVE_ARROW_ENABLED_SETTING.getValue())
  }

  // No evaluator while both features are off, so the device stops computing.
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

  var sideToMove by remember { mutableStateOf(explorer.engine.playerTurn) }
  val playedMoves by remember { derivedStateOf { buildMoveDisplays(playedSans, sideToMove) } }

  // LinesExplorer.state is snapshot state, so this recomposes on navigate, save and delete.
  val palette = LocalKineticPalette.current
  val nodeState = explorer.state
  val cornerTagLabel =
    if (viewerMode != null) viewerMode.cornerTag else stringResource(nodeStateLabel(nodeState))
  val cornerTagColor = if (readOnly) palette.ink2 else nodeStateColor(nodeState, palette)

  DisposableEffect(Unit) { onDispose { evaluator?.close() } }

  LaunchedEffect(evaluator) {
    evaluator?.evaluate(explorer.engine.toFen(), explorer.engine.playerTurn)
  }

  remember {
    explorer.registerCallBack {
      sideToMove = explorer.engine.playerTurn
      playedSans.clear()
      playedSans.addAll(explorer.playedMoves())
      coroutineScope.launch {
        val moves = explorer.getNextMoves()
        nextMoves.clear()
        yoursBySan.clear()
        nextMoves.addAll(moves)
        refreshYours(nextMoves, myTreeStore, explorer, yoursBySan)
      }
      evaluator?.let { eval ->
        coroutineScope.launch { eval.evaluate(explorer.engine.toFen(), explorer.engine.playerTurn) }
      }
    }
  }

  val content =
    ExploreLayoutContent(
      resetButton = {
        ControlButton.RESET.render(it) { coroutineScope.launch { explorer.reset() } }
      },
      reverseButton = { ControlButton.REVERSE.render(it) { inverted = !inverted } },
      backButton = { ControlButton.BACK.render(it) { coroutineScope.launch { explorer.back() } } },
      forwardButton = {
        ControlButton.FORWARD.render(it) { coroutineScope.launch { explorer.forward() } }
      },
      nextMoveButtons = {
        nextMoves.map<String, @Composable (() -> Unit)> {
          { NextMoveButton(it) { coroutineScope.launch { explorer.playMove(it) } } }
        }
      },
      playerTurnIndicator = { PlayerTurnIndicator(explorer) },
      stateIndicators = { if (!readOnly) StateIndicator(it, explorer.state) },
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
              interactionsManager = explorer,
              inverted = inverted,
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
          onSeek = { index ->
            // Reads the live depth each iteration so two quick taps cannot back past the root.
            coroutineScope.launch { while (explorer.depth > index + 1) explorer.back() }
          },
          modifier = trailModifier,
          openingName = null,
          pgnText = buildPgnText(playedMoves),
        )
      },
      controlBar = { ctrlModifier ->
        ExploreCtrlBar(
          modifier = ctrlModifier,
          actions =
            ExploreCtrlBarActions(
              onReset = { coroutineScope.launch { explorer.reset() } },
              onReverse = { inverted = !inverted },
              onBack = { coroutineScope.launch { explorer.back() } },
              onForward = { coroutineScope.launch { explorer.forward() } },
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
          sideToMove = sideToMove,
          showSaveDelete = !readOnly,
        )
      },
      sideInfo = { sideModifier ->
        ExploreSidebar(
          modifier = sideModifier,
          nextMoves = nextMoves.toList(),
          onPlayMove = { san -> coroutineScope.launch { explorer.playMove(san) } },
          explorerViewModel = explorerViewModel,
          onPlayLichessMove = { san -> coroutineScope.launch { explorer.playMove(san) } },
          isMine = { san -> yoursBySan[san] == true },
        )
      },
      mobileInfo = { infoModifier ->
        ExploreSidebar(
          modifier = infoModifier,
          nextMoves = nextMoves.toList(),
          onPlayMove = { san -> coroutineScope.launch { explorer.playMove(san) } },
          explorerViewModel = explorerViewModel,
          onPlayLichessMove = { san -> coroutineScope.launch { explorer.playMove(san) } },
          isMine = { san -> yoursBySan[san] == true },
        )
      },
      statBadges = { badgeModifier -> ExploreStatBadgesRow(modifier = badgeModifier) },
      cornerTagText = cornerTagLabel,
    )

  Column(modifier = Modifier.fillMaxSize()) {
    header()
    BoxWithConstraints(modifier = Modifier.weight(1f)) {
      if (maxHeight > maxWidth) PortraitExploreLayout(Modifier.fillMaxSize(), content)
      else LandscapeExploreLayout(Modifier.fillMaxSize(), content)
    }
  }
}

/** Marks each SAN in [moves] as YOURS when [myTreeStore] holds it as a good move here. */
private suspend fun refreshYours(
  moves: List<String>,
  myTreeStore: TreeStore?,
  explorer: LinesExplorer,
  yoursBySan: MutableMap<String, Boolean>,
) {
  val store = myTreeStore ?: return
  if (moves.isEmpty()) return
  val mineSans =
    store
      .node(explorer.engine.toPositionKey())
      ?.outgoing
      ?.values
      ?.filter { it.isGood == true }
      ?.map { it.move }
      ?.toSet()
      .orEmpty()
  yoursBySan.putAll(moves.associateWith { it in mineSans })
}

/** Short corner tag label for [state]. */
private fun nodeStateLabel(state: NodeState): StringResource =
  when (state) {
    NodeState.FIRST -> Res.string.tag_start
    NodeState.SAVED_GOOD -> Res.string.tag_saved
    NodeState.SAVED_BAD -> Res.string.tag_partial
    NodeState.SAVED_GOOD_BUT_UNKNOWN_MOVE -> Res.string.tag_saved_new
    NodeState.SAVED_BAD_BUT_UNKNOWN_MOVE -> Res.string.tag_partial_new
    NodeState.UNKNOWN -> Res.string.tag_not_saved
    NodeState.BAD_STATE -> Res.string.tag_conflict
  }

/** Corner tag label color for [state]. */
private fun nodeStateColor(
  state: NodeState,
  palette: proj.memorchess.axl.ui.theme.KineticPalette,
): Color =
  when (state) {
    NodeState.FIRST,
    NodeState.SAVED_GOOD -> palette.progress
    NodeState.SAVED_BAD,
    NodeState.SAVED_GOOD_BUT_UNKNOWN_MOVE,
    NodeState.SAVED_BAD_BUT_UNKNOWN_MOVE -> palette.streakText
    NodeState.UNKNOWN -> palette.ink3
    NodeState.BAD_STATE -> palette.destructive
  }

/**
 * Builds the moves trail chips from [sans], inferring colors and move numbers from the side to move
 * after them, since [proj.memorchess.axl.core.data.PositionKey] retains no full-move number.
 */
private fun buildMoveDisplays(
  sans: List<String>,
  sideToMove: Player,
): List<MoveDisplay> {
  if (sans.isEmpty()) return emptyList()
  val firstMover = if (sans.size % 2 == 1) sideToMove.opponent else sideToMove
  val numberOffset = if (firstMover == Player.WHITE) 0 else 1
  return sans.mapIndexed { index, san ->
    MoveDisplay(
      san = san,
      moveNumber = (index + numberOffset) / 2 + 1,
      mover = if (index % 2 == 0) firstMover else firstMover.opponent,
    )
  }
}

/** Minimal PGN movetext of [moves], e.g. `"1. e4 e5 2. Nf3"`, or `null` when empty. */
private fun buildPgnText(moves: List<MoveDisplay>): String? {
  if (moves.isEmpty()) return null
  return moves
    .mapIndexed { index, move ->
      val prefix =
        when {
          move.mover == Player.WHITE -> "${move.moveNumber}."
          index == 0 -> "${move.moveNumber}..."
          else -> null
        }
      if (prefix != null) "$prefix ${move.san}" else move.san
    }
    .joinToString(" ")
}

/** Eval bar ratio in `0f..1f` for [score], `0.5f` when `null`. */
private fun computeEvalRatio(score: EvaluationScore?): Float =
  when (score) {
    null -> 0.5f
    is EvaluationScore.Centipawns -> {
      val clamped = score.value.coerceIn(-600, 600).toFloat()
      0.5f + clamped / 1200f
    }
    is EvaluationScore.Mate -> if (score.moves >= 0) 1f else 0f
  }

/** Formats [score] for the eval readout: `"+0.3"`, `"M3"`, `"-M2"`. */
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
  var playerTurn by remember { mutableStateOf(explorer.engine.playerTurn) }
  remember { explorer.registerCallBack { playerTurn = explorer.engine.playerTurn } }
  Piece(ChessPiece(PieceKind.KING, playerTurn))
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
    Icon(
      imageVector = FeatherIcons.BarChart2,
      contentDescription = stringResource(Res.string.description_toggle_eval_bar),
    )
  }
}

/** The evaluator after a toggle change; closes [current] when [shouldRun] is `false`. */
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
