package proj.memorchess.axl.ui.components.control.board_control

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
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.SingleMoveTrainer
import proj.memorchess.axl.ui.components.board.Board
import proj.memorchess.axl.ui.components.loading.LoadingWidget
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

  private val isCorrect = mutableStateOf<Boolean?>(null)
  private val daysInAdvance = mutableStateOf(0)
  private val reloader = BasicReloader()
  private val moveDelay = TRAINING_MOVE_DELAY_SETTING.getValue()

  @Composable
  fun Draw(modifier: Modifier = Modifier) {
    val localReloader = remember { BasicReloader() }
    val moveToTrain =
      remember(localReloader.getKey(), daysInAdvance.value) {
        NodeManager.getNextNodeToLearn(daysInAdvance.value)
      }
    LaunchedEffect(reloader.getKey()) {
      if (isCorrect.value != null) {
        delay(moveDelay)
        if (isCorrect.value == true) {
          isCorrect.value = null
        }
        localReloader.reload()
      }
    }
    if (moveToTrain == null) {
      NoNodeToTrain(modifier = modifier)
    } else {
      NodeToTrain(moveToTrain, modifier = modifier)
    }
  }

  /**
   * Composable that displays a message when there are no nodes to train.
   *
   * @param modifier Modifier for styling.
   */
  @Composable
  private fun NoNodeToTrain(modifier: Modifier = Modifier) {
    if (isCorrect.value == false && daysInAdvance.value > 0) {
      daysInAdvance.value = 1
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
          tint = Color(0xFF4CAF50),
          modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "Bravo !",
          style = MaterialTheme.typography.headlineMedium,
          color = Color(0xFF333333),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "You have finished today's training!",
          style = MaterialTheme.typography.bodyLarge,
          color = Color(0xFF666666),
          textAlign = TextAlign.Center,
        )
        Button(onClick = { daysInAdvance.value++ }, modifier = Modifier.padding(top = 16.dp)) {
          Text("Increment a day")
        }
        Text("Days in advance: ${daysInAdvance.value}")
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
  private fun NodeToTrain(nodeToLearn: StoredNode, modifier: Modifier = Modifier) {
    val trainer = remember(nodeToLearn) { SingleMoveTrainer(nodeToLearn, isCorrect) }
    val inverted = remember(nodeToLearn) { trainer.game.position.playerTurn == Game.Player.BLACK }
    Column(
      modifier = modifier.fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.Top,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Board(
        inverted,
        trainer,
        reloader,
        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
      )
      Spacer(modifier = Modifier.height(24.dp))
      Text(
        text = "Days in advance: ${daysInAdvance.value}",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFF666666),
        modifier = Modifier.align(Alignment.CenterHorizontally),
      )
      // Icon based on isCorrect
      if (isCorrect.value != null) {
        val icon = if (isCorrect.value == true) Icons.Default.Done else Icons.Default.Close
        val tint = if (isCorrect.value == true) Color(0xFF4CAF50) else Color(0xFFF44336)
        Icon(
          imageVector = icon,
          contentDescription = if (isCorrect.value == true) "Correct" else "Incorrect",
          tint = tint,
          modifier = Modifier.size(32.dp).align(Alignment.CenterHorizontally),
        )
      }
    }
  }
}
