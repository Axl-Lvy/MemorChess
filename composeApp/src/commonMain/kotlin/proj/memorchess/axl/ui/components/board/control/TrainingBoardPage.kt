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
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import proj.memorchess.axl.core.config.TRAINING_MOVE_DELAY_SETTING
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.NodeManager
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

/** Training board */
@Composable
fun TrainingBoardPage(modifier: Modifier = Modifier, nodeManager: NodeManager = koinInject()) {
  val trainingBoard = remember { TrainingBoard() }
  LoadingWidget({ nodeManager.resetCacheFromDataBase() }) {
    trainingBoard.Draw(modifier = modifier)
  }
}

/**
 * TrainingBoard manages the state and logic for the training board UI in the application. It
 * handles move validation, scheduling, and reloading for the training session.
 */
private class TrainingBoard : KoinComponent {

  private var state by mutableStateOf(TrainingBoardState.FROM_CORRECT_MOVE)
  private var daysInAdvance by mutableStateOf(0)
  private val reloader = BasicReloader()
  private val moveDelay = TRAINING_MOVE_DELAY_SETTING.getValue()
  private var previousPlayedMove: StoredMove? = null
  private val nodeManager: NodeManager by inject()
  private val localReloader = BasicReloader()
  private val trainerReloader = BasicReloader()
  private var chosenNode by mutableStateOf<StoredNode?>(null)

  init {
    choseNextNode()
  }

  @Composable
  fun Draw(modifier: Modifier = Modifier) {
    val numberOfNodesToTrain =
      remember(localReloader.getKey(), daysInAdvance) {
        nodeManager.getNumberOfNodesToTrain(daysInAdvance)
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
    chosenNode = nodeManager.getNextNodeToLearn(daysInAdvance, previousPlayedMove)
   }

  /**
   * Composable that displays a message when there are no nodes to train.
   *
   * @param modifier Modifier for styling.
   */
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

  /**
   * Composable based on a node to train.
   *
   * @param nodeToLearn The node to learn.
   * @param numberOfNodesToTrain The number of nodes to train.
   * @param modifier Modifier for styling.
   */
  @Composable
  private fun NodeToTrain(
    nodeToLearn: StoredNode,
    numberOfNodesToTrain: Int,
    modifier: Modifier = Modifier,
  ) {
    val trainer = remember {
      val trainer =
        SingleMoveTrainer(nodeToLearn) {
          state =
            if (it != null) TrainingBoardState.SHOW_CORRECT_MOVE
            else TrainingBoardState.SHOW_WRONG_MOVE
          previousPlayedMove = it
        }
      trainer.registerCallBack { reloader.reload() }
      trainer
    }
    trainer.updateNode(nodeToLearn)
    val inverted = remember(nodeToLearn) { trainer.game.position.playerTurn == Game.Player.BLACK }
    val content =
      TrainingLayoutContent(
        board = { BoardContainer(inverted, trainer, it) },
        daysInAdvance = { DaysInAdvanceCard(this@TrainingBoard.daysInAdvance, it) },
        successIndicator = {
          SuccessIndicatorCard(
            state.isCorrect,
            state.isShowing,
            { choseNextNode() },
            chosenNode?.positionIdentifier,
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
