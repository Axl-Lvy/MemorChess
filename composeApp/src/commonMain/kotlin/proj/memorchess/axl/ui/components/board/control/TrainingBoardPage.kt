package proj.memorchess.axl.ui.components.board.control

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import proj.memorchess.axl.core.config.TRAINING_MOVE_DELAY_SETTING
import proj.memorchess.axl.core.data.StoredMove
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.SingleMoveTrainer
import proj.memorchess.axl.ui.components.board.Board
import proj.memorchess.axl.ui.components.board.BoardTopping
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.theme.goodTint
import proj.memorchess.axl.ui.util.BasicReloader

/** Training board */
@Composable
fun TrainingBoardPage(modifier: Modifier = Modifier) {
  val trainingBoard = remember { TrainingBoard() }
  LoadingWidget({ NodeManager.resetCacheFromDataBase() }) {
    trainingBoard.Draw(modifier = modifier)
  }
}

/**
 * TrainingBoard manages the state and logic for the training board UI in the application. It
 * handles move validation, scheduling, and reloading for the training session.
 */
private class TrainingBoard {

  private var state by mutableStateOf(TrainingBoardState.FROM_CORRECT_MOVE)
  private var daysInAdvance by mutableStateOf(0)
  private val reloader = BasicReloader()
  private val moveDelay = TRAINING_MOVE_DELAY_SETTING.getValue()
  private var previousPlayedMove: StoredMove? = null

  @Composable
  fun Draw(modifier: Modifier = Modifier) {
    val localReloader = remember { BasicReloader() }
    val numberOfNodesToTrain =
      remember(localReloader.getKey(), daysInAdvance) {
        NodeManager.getNumberOfNodesToTrain(daysInAdvance)
      }
    val moveToTrain =
      remember(localReloader.getKey(), daysInAdvance) {
        NodeManager.getNextNodeToLearn(daysInAdvance, previousPlayedMove)
      }
    LaunchedEffect(reloader.getKey()) {
      if (state.isShowing) {
        delay(moveDelay)
        state = state.toPlayableState()
        localReloader.reload()
      }
    }
    if (moveToTrain == null) {
      NoNodeToTrain(modifier = modifier)
    } else {
      NodeToTrain(moveToTrain, numberOfNodesToTrain, modifier = modifier)
    }
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
        Button(onClick = { daysInAdvance++ }, modifier = Modifier.padding(top = 16.dp)) {
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
   * @param modifier Modifier for styling.
   */
  @Composable
  private fun NodeToTrain(
    nodeToLearn: StoredNode,
    numberOfNodesToTrain: Int,
    modifier: Modifier = Modifier,
  ) {
    val trainer by
      remember(nodeToLearn) {
        mutableStateOf(
          SingleMoveTrainer(nodeToLearn) {
            state =
              if (it != null) TrainingBoardState.SHOW_CORRECT_MOVE
              else TrainingBoardState.SHOW_WRONG_MOVE
            previousPlayedMove = it
          }
        )
      }
    val inverted = remember(nodeToLearn) { trainer.game.position.playerTurn == Game.Player.BLACK }
    Column(
      modifier = modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.Top,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      NumberOfNodeToTrainIndicator(numberOfNodesToTrain)
      Spacer(modifier = Modifier.height(16.dp))
      Board(
        inverted,
        trainer,
        reloader,
        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
      )
      Spacer(modifier = Modifier.height(24.dp))
      Text(
        text = "Days in advance: $daysInAdvance",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.align(Alignment.CenterHorizontally),
      )
      // Icon based on isCorrect
      if (state.isShowing) {
        val icon = if (state.isCorrect) Icons.Default.Done else Icons.Default.Close
        val tint = if (state.isCorrect) Color(0xFF4CAF50) else Color(0xFFF44336)
        Icon(
          imageVector = icon,
          contentDescription = if (state.isCorrect) "Correct" else "Incorrect",
          tint = tint,
          modifier = Modifier.size(32.dp).align(Alignment.CenterHorizontally),
        )
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
      else -> error { "$this is already playable" }
    }
  }
}

@Composable
private fun NumberOfNodeToTrainIndicator(numberOfNodesToTrain: Int, modifier: Modifier = Modifier) {
  // Color interpolation from ErrorContainer to goodTint
  val minAlpha = 0.15f
  val maxAlpha = 0.5f
  val minNodes = 1 // never 0
  val maxNodes = 20 // arbitrary upper bound for scaling
  val clampedNodes = numberOfNodesToTrain.coerceIn(minNodes, maxNodes)
  val fraction = 1 - (clampedNodes - minNodes).toFloat() / (maxNodes - minNodes)
  val startColor = MaterialTheme.colorScheme.errorContainer
  val endColor = goodTint
  fun lerpColor(a: Color, b: Color, t: Float): Color {
    return Color(
      red = a.red + (b.red - a.red) * t,
      green = a.green + (b.green - a.green) * t,
      blue = a.blue + (b.blue - a.blue) * t,
      alpha = 1f,
    )
  }
  val baseColor = lerpColor(startColor, endColor, fraction)
  val alpha = maxAlpha - fraction * (maxAlpha - minAlpha)
  val color = baseColor.copy(alpha = alpha)
  BoardTopping(backGroundColor = color, modifier = modifier) {
    Text(
      text = "Moves to train: $numberOfNodesToTrain",
      color = baseColor,
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}
