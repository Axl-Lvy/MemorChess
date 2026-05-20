package proj.memorchess.axl.ui.components.board.control

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import org.koin.compose.koinInject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import proj.memorchess.axl.core.config.TRAINING_MOVE_DELAY_SETTING
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.graph.Edge
import proj.memorchess.axl.core.graph.Node
import proj.memorchess.axl.core.graph.TrainingScheduler
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.core.interactions.SingleMoveTrainer
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.components.training.BoardContainer
import proj.memorchess.axl.ui.components.training.DaysInAdvanceCard
import proj.memorchess.axl.ui.components.training.MovesToTrainCard
import proj.memorchess.axl.ui.components.training.SuccessIndicatorCard
import proj.memorchess.axl.ui.layout.training.LandscapeTrainingLayout
import proj.memorchess.axl.ui.layout.training.PortraitTrainingLayout
import proj.memorchess.axl.ui.layout.training.TrainingLayoutContent
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

  init {
    choseNextNode()
  }

  @Composable
  fun Draw(modifier: Modifier = Modifier) {
    val numberOfNodesToTrain =
      remember(localReloader.getKey(), daysInAdvance) {
        trainingScheduler.pendingCount(dayOffset(daysInAdvance))
      }
    LaunchedEffect(reloader.getKey()) {
      if (state.isShowing && state.isCorrect) {
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
    chosenNode = entry?.let { treeStore.current().get(it.positionKey) }
  }

  @Composable
  private fun NoNodeToTrain(modifier: Modifier = Modifier) {
    if (!state.isCorrect && daysInAdvance > 0) {
      daysInAdvance = 1
      return
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Icon(
          imageVector = Icons.Default.Done,
          contentDescription = "Congratulations",
          tint = goodTint,
          modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "Bravo !",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "You have finished today's training!",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center,
        )
        Button(
          onClick = {
            daysInAdvance++
            choseNextNode()
          },
          modifier = Modifier.padding(top = 16.dp),
        ) {
          Text("Increment a day")
        }
        Text("Days in advance: $daysInAdvance")
      }
    }
  }

  @Composable
  private fun NodeToTrain(
    nodeToLearn: Node,
    numberOfNodesToTrain: Int,
    modifier: Modifier = Modifier,
  ) {
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
    trainer.updateNode(nodeToLearn)
    val inverted = remember(nodeToLearn) { trainer.engine.playerTurn == Player.BLACK }
    val content =
      TrainingLayoutContent(
        board = { BoardContainer(inverted, trainer, it) },
        daysInAdvance = { DaysInAdvanceCard(this@TrainingBoard.daysInAdvance, it) },
        successIndicator = {
          SuccessIndicatorCard(
            state.isCorrect,
            state.isShowing,
            { choseNextNode() },
            chosenNode?.positionKey,
            it,
          )
        },
        movesToTrain = { MovesToTrainCard(numberOfNodesToTrain, it) },
      )
    BoxWithConstraints {
      if (maxHeight > maxWidth) {
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
