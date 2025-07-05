package proj.memorchess.axl.ui.components.control.board_control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Save
import compose.icons.feathericons.Trash
import kotlinx.coroutines.launch
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.ui.components.board.Board
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.util.BasicReloader

@Composable
fun ControllableBoard(modifier: Modifier = Modifier) {
  LoadingWidget({ NodeManager.resetCacheFromDataBase() }) { Component(modifier) }
}

@Composable
private fun Component(modifier: Modifier = Modifier) {
  var inverted by remember { mutableStateOf(false) }
  val boardReloader = remember { BasicReloader() }
  val linesExplorer = remember { LinesExplorer() }
  val coroutineScope = rememberCoroutineScope()

  val nextMoves = remember(boardReloader.getKey()) { linesExplorer.getNextMoves() }
  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    modifier = modifier.padding(16.dp),
  ) {
    ControlBar(
      modifier = Modifier.height(50.dp),
      onReverseClick = {
        inverted = !inverted
        boardReloader.reload()
      },
      onResetClick = { linesExplorer.reset(boardReloader) },
      onForwardClick = { linesExplorer.forward(boardReloader) },
      onBackClick = { linesExplorer.back(boardReloader) },
      playerTurn = linesExplorer.game.position.playerTurn,
    )
    Board(inverted, linesExplorer, boardReloader, modifier = modifier.fillMaxWidth())
    NextMoveBar(moveList = nextMoves, playMove = { linesExplorer.playMove(it, boardReloader) })
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Button(
        onClick = { coroutineScope.launch { linesExplorer.saveGood() } },
        colors = ButtonDefaults.buttonColors(Color.Green),
        modifier = Modifier.weight(1f),
      ) {
        Icon(FeatherIcons.Save, contentDescription = "Save Good")
      }
      Button(
        onClick = { coroutineScope.launch { linesExplorer.saveBad() } },
        colors = ButtonDefaults.buttonColors(Color.Yellow),
        modifier = Modifier.weight(1f),
      ) {
        Icon(FeatherIcons.Save, contentDescription = "Save Bad")
      }
      Button(
        onClick = { coroutineScope.launch { linesExplorer.delete(boardReloader) } },
        colors = ButtonDefaults.buttonColors(Color.Red),
        modifier = Modifier.weight(1f),
      ) {
        Icon(FeatherIcons.Trash, contentDescription = "Delete")
      }
    }
  }
}
