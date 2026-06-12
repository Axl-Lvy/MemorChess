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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.training_congratulations
import memorchess.composeapp.generated.resources.training_corner_tag
import memorchess.composeapp.generated.resources.training_days_in_advance
import memorchess.composeapp.generated.resources.training_finished_subtitle
import memorchess.composeapp.generated.resources.training_finished_title
import memorchess.composeapp.generated.resources.training_increment_day
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import proj.memorchess.axl.core.config.TRAINING_MOVE_DELAY_SETTING
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.engine.GameEngine
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.graph.Edge
import proj.memorchess.axl.core.graph.Node
import proj.memorchess.axl.core.graph.TrainingScheduler
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.interactions.SingleMoveTrainer
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonLabel
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.components.explore.MovesTrail
import proj.memorchess.axl.ui.components.loading.LoadingWidget
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

/** Training board entry point. Loads the tree on entry. */
@Composable
fun TrainingBoardPage(modifier: Modifier = Modifier, treeStore: TreeStore = koinInject()) {
  LoadingWidget({ treeStore.load() }) {
    val trainingBoard = remember { TrainingBoard() }
    trainingBoard.Draw(modifier = modifier)
  }
}

/** State holder for the training session UI. */
private class TrainingBoard : KoinComponent {

  private var state by mutableStateOf(TrainingBoardState.FROM_CORRECT_MOVE)
  private var daysInAdvance by mutableStateOf(0)
  private val reloader = BasicReloader()
  private val moveDelay = TRAINING_MOVE_DELAY_SETTING.getValue()
  private var previousPlayedEdge: Edge? = null
  private val treeStore: TreeStore by inject()
  private val trainingScheduler: TrainingScheduler by inject()
  private val localReloader = BasicReloader()
  private val trainerReloader = BasicReloader()
  private var chosenNode by mutableStateOf<Node?>(null)

  // Session counters — live for the lifetime of this `TrainingBoard` instance (i.e. the lifetime
  // of the page). When the user leaves Training and comes back, the page is recreated and these
  // reset to 0.
  private var successCount by mutableStateOf(0)
  private var failCount by mutableStateOf(0)

  init {
    choseNextNode()
  }

  @Composable
  fun Draw(modifier: Modifier = Modifier) {
    val numberOfNodesToTrain =
      remember(localReloader.getKey(), daysInAdvance) {
        trainingScheduler.pendingCount(dayOffset(daysInAdvance))
      }
    // Count each attempt exactly once: increment on the transition into a SHOW_* state. Using
    // LaunchedEffect(state) means we react only when `state` actually changes value, and the
    // `isShowing` guard ensures FROM_* → FROM_* transitions are ignored. After incrementing we
    // continue the existing "auto-advance on correct" behavior.
    LaunchedEffect(state) {
      if (state.isShowing) {
        if (state.isCorrect) {
          successCount += 1
        } else {
          failCount += 1
        }
      }
    }
    // Anki-style auto-advance: after the user plays — correct or not — we wait moveDelay then move
    // on to the next entry. The wrong card has already been graded AGAIN by the trainer's
    // afterPlayMove, so the scheduler will re-surface it on its own schedule; no need for a manual
    // "Next" button. Auto-advance fires for both SHOW_CORRECT_MOVE and SHOW_WRONG_MOVE states.
    LaunchedEffect(reloader.getKey()) {
      if (state.isShowing) {
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

  private fun choseNextNode() {
    state = state.toPlayableState()
    localReloader.reload()
    trainerReloader.reload()
    val day = dayOffset(daysInAdvance)
    val previousEdge = previousPlayedEdge
    val entry =
      if (previousEdge == null) {
        trainingScheduler.nextDue(day)
      } else {
        trainingScheduler.nextAfter(previousEdge.to, day) ?: trainingScheduler.nextDue(day)
      }
    chosenNode = entry?.let { treeStore.current()[it.positionKey] }
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
            choseNextNode()
          },
          style = KineticButtonStyle.Primary,
        ) {
          KineticButtonLabel(stringResource(Res.string.training_increment_day))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(Res.string.training_days_in_advance, daysInAdvance),
          style = typography.monoSm.copy(color = palette.ink3),
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
    val trainer = remember {
      val trainer =
        SingleMoveTrainer(nodeToLearn) {
          state =
            if (it != null) TrainingBoardState.SHOW_CORRECT_MOVE
            else TrainingBoardState.SHOW_WRONG_MOVE
          previousPlayedEdge = it
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
    // Derive board orientation from nodeToLearn directly, not from `trainer.engine`. The trainer's
    // engine has not yet been swapped to the new entry at this point in composition (updateNode
    // runs in the LaunchedEffect, after composition), so reading trainer.engine.playerTurn here
    // returns the player-to-move of the OLD engine — i.e. the opponent's color after the user's
    // last move. That mis-derivation was causing the board to flip whenever a new entry loaded.
    val inverted =
      remember(nodeToLearn.positionKey) {
        GameEngine(nodeToLearn.positionKey).playerTurn == Player.BLACK
      }

    val totalAttempts = successCount + failCount
    val denominator = totalAttempts + numberOfNodesToTrain
    val progress =
      if (denominator > 0) {
        (totalAttempts.toFloat() / denominator.toFloat()).coerceIn(0f, 1f)
      } else {
        0f
      }
    val playerTurn = trainer.engine.playerTurn

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
              attempt = totalAttempts,
              success = state.isCorrect,
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
              onSkip = { choseNextNode() },
              onHint = {}, // stub for v1
              onReveal = {}, // stub for v1
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
