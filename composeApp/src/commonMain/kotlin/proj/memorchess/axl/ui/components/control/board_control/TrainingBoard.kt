package proj.memorchess.axl.ui.components.control.board_control

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import proj.memorchess.axl.core.data.StoredNode
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.SingleLineTrainer
import proj.memorchess.axl.core.util.IReloader
import proj.memorchess.axl.ui.components.board.Board
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.util.BasicReloader

/** Training board */
@Composable
fun TrainingBoard(modifier: Modifier = Modifier) {
  LoadingWidget({ NodeManager.resetCacheFromDataBase() }) { Component(modifier) }
}

@Composable
private fun Component(modifier: Modifier = Modifier) {
  val reloader = remember { BasicReloader() }
  var daysFromToday = remember(reloader.getKey()) { 0 }
  val moveToTrain = remember(reloader.getKey()) { NodeManager.getNextNodeToLearn(daysFromToday) }
  if (moveToTrain == null) {
    NoNodeToTrain(modifier = modifier) { daysFromToday++ }
  } else {
    NodeToTrain(moveToTrain, reloader, modifier = modifier)
  }
}

/**
 * Composable that displays a message when there are no nodes to train.
 *
 * @param modifier Modifier for styling.
 * @param incrementDays Function to call when the button is clicked to increment the days. This
 *   allows to train with nodes from tomorrow.
 */
@Composable
private fun NoNodeToTrain(modifier: Modifier = Modifier, incrementDays: () -> Unit) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = Icons.Default.Done,
        contentDescription = "Félicitations",
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
        text = "Vous avez terminé toutes les positions à apprendre pour aujourd'hui.",
        style = MaterialTheme.typography.bodyLarge,
        color = Color(0xFF666666),
        textAlign = TextAlign.Center,
      )
      Button(onClick = incrementDays, modifier = Modifier.padding(top = 16.dp)) {
        Text("Increment a day")
      }
    }
  }
}

/**
 * Composable based on a node to train.
 *
 * @param nodeToLearn The node to learn.
 * @param modifier Modifier for styling.
 * @param reloader The reloader to use for refreshing the board.
 */
@Composable
private fun NodeToTrain(
  nodeToLearn: StoredNode,
  reloader: IReloader,
  modifier: Modifier = Modifier,
) {
  val trainer = remember(nodeToLearn) { SingleLineTrainer(nodeToLearn) }
  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    modifier = modifier.padding(16.dp),
  ) {
    Board(
      trainer.game.position.playerTurn == Game.Player.BLACK,
      trainer,
      reloader,
      modifier = modifier.fillMaxWidth(),
    )
  }
}
