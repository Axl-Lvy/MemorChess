package proj.memorchess.axl.ui.components.board.control

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.training_congratulations
import memorchess.composeapp.generated.resources.training_corner_tag
import memorchess.composeapp.generated.resources.training_days_in_advance
import memorchess.composeapp.generated.resources.training_finished_subtitle
import memorchess.composeapp.generated.resources.training_finished_title
import memorchess.composeapp.generated.resources.training_hint_confirm
import memorchess.composeapp.generated.resources.training_increment_day
import memorchess.composeapp.generated.resources.training_reveal_confirm
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import proj.memorchess.axl.core.config.TRAINING_MOVE_DELAY_SETTING
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.engine.BoardLocation
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.graph.Edge
import proj.memorchess.axl.core.graph.Node
import proj.memorchess.axl.core.graph.TrainingScheduler
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.interactions.SingleMoveTrainer
import proj.memorchess.axl.ui.components.board.BoardTrainingFeedback
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonLabel
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.components.explore.MovesTrail
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.components.training.BoardContainer
import proj.memorchess.axl.ui.components.training.KineticProgressRail
import proj.memorchess.axl.ui.components.training.TrainingCounterRow
import proj.memorchess.axl.ui.components.training.TrainingCtrlBar
import proj.memorchess.axl.ui.layout.training.LandscapeTrainingLayout
import proj.memorchess.axl.ui.layout.training.PortraitTrainingLayout
import proj.memorchess.axl.ui.layout.training.TrainingLayoutContent
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography
import proj.memorchess.axl.ui.theme.goodTint
import proj.memorchess.axl.ui.util.BasicReloader

/**
 * Training board entry point. Selects the first card on entry. [repertoireId] narrows the session
 * to one repertoire, or `null` for the unscoped default.
 */
@Composable
fun TrainingBoardPage(modifier: Modifier = Modifier, repertoireId: String? = null) {
  val trainingBoard = remember(repertoireId) { TrainingBoard(repertoireId) }
  // The initial card selection is suspendable, so it runs inside the loading phase: the board only
  // renders once the first card is chosen, avoiding a transient "nothing to train" flash before
  // selection completes. The graph is demand paged, so no eager load is needed.
  LoadingWidget({ trainingBoard.choseNextNode() }) { trainingBoard.Draw(modifier = modifier) }
}

/** State holder for the training session UI. */
private class TrainingBoard(private val repertoireId: String? = null) : KoinComponent {

  private var state by mutableStateOf(TrainingBoardState.FROM_CORRECT_MOVE)
  private var daysInAdvance by mutableStateOf(0)
  private val reloader = BasicReloader()
  private val moveDelay = TRAINING_MOVE_DELAY_SETTING.getValue()
  private var previousPlayedEdge: Edge? = null
  private var previousPlayedSquare by mutableStateOf<BoardLocation?>(null)
  private var previousCorrectSquare by mutableStateOf<BoardLocation?>(null)
  private val treeStore: TreeStore by inject()
  private val trainingScheduler: TrainingScheduler by inject()
  private val localReloader = BasicReloader()
  private val trainerReloader = BasicReloader()
  private var chosenNode by mutableStateOf<Node?>(null)
  // Squares outlined by HINT / REVEAL for the current card.
  private var hintSquares by mutableStateOf(emptySet<BoardLocation>())
  // Whether the current card was failed by a wrong move, a hint or a reveal. A failed card counts
  // once as a fail and is retried until the right move is played, which then counts nothing.
  private var failed by mutableStateOf(false)
  // Monotonic count of graded moves, driving the board feedback animations once per move.
  private var attempt by mutableStateOf(0)
  private val assistConfirmation = ConfirmationDialog()

  // Session counters — live for the lifetime of this `TrainingBoard` instance (i.e. the lifetime
  // of the page). When the user leaves Training and comes back, the page is recreated and these
  // reset to 0.
  private var successCount by mutableStateOf(0)
  private var failCount by mutableStateOf(0)

  @Composable
  fun Draw(modifier: Modifier = Modifier) {
    val numberOfNodesToTrain by
      produceState(0, localReloader.getKey(), daysInAdvance) {
        value = trainingScheduler.pendingCount(dayOffset(daysInAdvance), repertoireId)
      }
    // Auto-advance after a correct move: wait moveDelay then move on to the next entry. A wrong
    // move is retried on the same card instead, see NodeToTrain.
    LaunchedEffect(reloader.getKey()) {
      if (state == TrainingBoardState.SHOW_CORRECT_MOVE) {
        delay(moveDelay)
        choseNextNode()
      }
    }
    val finalChosenNode = chosenNode
    if (finalChosenNode == null) {
      NoNodeToTrain(modifier = modifier)
    } else {
      NodeToTrain(finalChosenNode, numberOfNodesToTrain, modifier = modifier)
    }
  }

  private fun showHint(squares: Set<BoardLocation>) {
    hintSquares = hintSquares + squares
    markFailed()
  }

  /** Runs [assist] right away on a failed card, otherwise once the user confirms [warning]. */
  private fun confirmAssist(warning: String, assist: () -> Unit) {
    if (failed) assist() else assistConfirmation.show(warning, assist)
  }

  private fun markFailed() {
    if (!failed) {
      failed = true
      failCount += 1
    }
  }

  /**
   * Selects the next card to train and updates [chosenNode]. Suspends because it queries the
   * bounded scheduling surface. Called for the initial selection during the loading phase and after
   * every move or skip.
   */
  suspend fun choseNextNode() {
    // A failed card was graded AGAIN, so it collapses the days-in-advance window.
    state = if (failed) TrainingBoardState.FROM_WRONG_MOVE else state.toPlayableState()
    hintSquares = emptySet()
    failed = false
    localReloader.reload()
    trainerReloader.reload()
    val day = dayOffset(daysInAdvance)
    val previousEdge = previousPlayedEdge
    val entry =
      if (previousEdge == null) {
        trainingScheduler.nextDue(day, repertoireId)
      } else {
        trainingScheduler.nextAfter(previousEdge.to, day, repertoireId)
          ?: trainingScheduler.nextDue(day, repertoireId)
      }
    chosenNode = entry?.let { treeStore.node(it.positionKey) }
  }

  @Composable
  private fun NoNodeToTrain(modifier: Modifier = Modifier) {
    if (!state.isCorrect && daysInAdvance > 1) {
      // A failed review while training days in advance collapses the window back to one day, so
      // the user reviews the forgotten card again soon instead of keeping on burning future days.
      // This must also re-run node selection: writing daysInAdvance alone only recomposes, and
      // when the deck at the collapsed window is empty the early return would otherwise leave the
      // screen blank.
      LaunchedEffect(Unit) {
        daysInAdvance = 1
        choseNextNode()
      }
      return
    }
    val palette = LocalKineticPalette.current
    val typography = LocalKineticTypography.current
    val scope = rememberCoroutineScope()
    Box(
      modifier = modifier.fillMaxSize().background(palette.bg),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Icon(
          imageVector = Icons.Default.Done,
          contentDescription = stringResource(Res.string.training_congratulations),
          tint = goodTint,
          modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = stringResource(Res.string.training_finished_title),
          style = typography.displayLg.copy(color = palette.ink),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(Res.string.training_finished_subtitle),
          style = typography.body.copy(color = palette.ink2),
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        KineticButton(
          onClick = {
            daysInAdvance++
            scope.launch { choseNextNode() }
          },
          style = KineticButtonStyle.Primary,
        ) {
          KineticButtonLabel(stringResource(Res.string.training_increment_day))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(Res.string.training_days_in_advance, daysInAdvance),
          style = typography.labelSm.copy(color = palette.ink3),
        )
      }
    }
  }

  @Composable
  private fun NodeToTrain(
    nodeToLearn: Node,
    numberOfNodesToTrain: Int,
    modifier: Modifier = Modifier,
  ) {
    val cornerTag = stringResource(Res.string.training_corner_tag)
    val hintWarning = stringResource(Res.string.training_hint_confirm)
    val revealWarning = stringResource(Res.string.training_reveal_confirm)
    val scope = rememberCoroutineScope()
    val trainer = remember {
      val trainer =
        SingleMoveTrainer(nodeToLearn, repertoireScope = repertoireId) {
          edge,
          playedSquare,
          correctSquare ->
          attempt += 1
          if (edge == null) {
            markFailed()
          } else if (!failed) {
            successCount += 1
          }
          state =
            if (edge != null) TrainingBoardState.SHOW_CORRECT_MOVE
            else TrainingBoardState.SHOW_WRONG_MOVE
          previousPlayedEdge = edge
          previousPlayedSquare = playedSquare
          previousCorrectSquare = correctSquare
        }
      trainer.registerCallBack { reloader.reload() }
      trainer
    }
    // Swap the trainer's node only when the training entry actually changes. Calling updateNode on
    // every recomposition (e.g. when `state` flips to SHOW_CORRECT_MOVE right after the user's
    // move)
    // would re-trigger its "engine drifted past node" reset path — that's what caused the
    // user-visible "move plays → rollback → both moves animate together" glitch: the rollback came
    // from the reset, and the combined animation came from the next entry's updateNode diffing the
    // pre-move position against the next training position.
    LaunchedEffect(nodeToLearn) { trainer.updateNode(nodeToLearn) }
    // A wrong move stays on screen for moveDelay, then the same position comes back for a retry.
    LaunchedEffect(state) {
      if (state == TrainingBoardState.SHOW_WRONG_MOVE) {
        delay(moveDelay)
        state = TrainingBoardState.FROM_WRONG_MOVE
        trainer.retry()
      }
    }
    // Derive the side to move from nodeToLearn, not from `trainer.engine`: the engine is swapped to
    // the new entry only in the LaunchedEffect above, after composition, and is not snapshot state,
    // so reading it here would keep the previous card's turn until something else recomposes.
    val playerTurn =
      remember(nodeToLearn.positionKey) { GameEngine(nodeToLearn.positionKey).playerTurn }
    val inverted = playerTurn == Player.BLACK

    val totalAttempts = successCount + failCount
    val denominator = totalAttempts + numberOfNodesToTrain
    val progress =
      if (denominator > 0) {
        (totalAttempts.toFloat() / denominator.toFloat()).coerceIn(0f, 1f)
      } else {
        0f
      }
    val feedback =
      BoardTrainingFeedback(
        playedSquare = previousPlayedSquare.takeIf { state.isShowing },
        correctSquare = previousCorrectSquare.takeIf { state.isShowing && !state.isCorrect },
        isCorrect = state.isCorrect.takeIf { state.isShowing },
        attempt = attempt,
        hintSquares = if (state.isShowing) emptySet() else hintSquares,
      )

    BoxWithConstraints {
      val portrait = maxHeight > maxWidth
      val content =
        TrainingLayoutContent(
          board = { mod ->
            BoardContainer(
              inverted = inverted,
              trainer = trainer,
              modifier = mod,
              compact = portrait,
              cornerTagText = cornerTag,
              feedback = feedback,
            )
          },
          counters = { mod ->
            TrainingCounterRow(
              successCount = successCount,
              failCount = failCount,
              leftCount = numberOfNodesToTrain,
              modifier = mod,
            )
          },
          progress = { mod -> KineticProgressRail(progress = progress, modifier = mod) },
          movesTrail = { mod ->
            // v1: empty trail. Acceptable per the Explore precedent; the Training visual port does
            // not yet surface the line history.
            MovesTrail(moves = emptyList(), currentIndex = -1, onSeek = {}, modifier = mod)
          },
          controlBar = { mod ->
            TrainingCtrlBar(
              playerTurn = playerTurn,
              onSkip = { scope.launch { choseNextNode() } },
              onHint = {
                if (!state.isShowing) {
                  confirmAssist(hintWarning) {
                    scope.launch { trainer.hint()?.let { showHint(setOf(it)) } }
                  }
                }
              },
              onReveal = {
                if (!state.isShowing) {
                  confirmAssist(revealWarning) {
                    scope.launch {
                      trainer.reveal()?.let { (from, to) -> showHint(setOf(from, to)) }
                    }
                  }
                }
              },
              feedbackActive = state == TrainingBoardState.SHOW_WRONG_MOVE,
              failed = failed,
              modifier = mod,
            )
          },
          cornerTagText = cornerTag,
        )
      if (portrait) {
        PortraitTrainingLayout(content = content, modifier = modifier)
      } else {
        LandscapeTrainingLayout(content = content, modifier = modifier)
      }
    }
    assistConfirmation.DrawDialog()
  }
}

private fun dayOffset(daysInAdvance: Int): LocalDate {
  val tz = TimeZone.currentSystemDefault()
  val today = (DateUtil.now() + daysInAdvance.days).toLocalDateTime(tz).date
  return today
}

private enum class TrainingBoardState(val isCorrect: Boolean, val isShowing: Boolean) {
  SHOW_CORRECT_MOVE(true, true),
  SHOW_WRONG_MOVE(false, true),
  FROM_CORRECT_MOVE(true, false),
  FROM_WRONG_MOVE(false, false);

  fun toPlayableState(): TrainingBoardState {
    return when (this) {
      SHOW_CORRECT_MOVE -> FROM_CORRECT_MOVE
      SHOW_WRONG_MOVE -> FROM_WRONG_MOVE
      else -> this
    }
  }
}
