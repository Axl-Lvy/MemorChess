package proj.ankichess.axl.ui.components.control.board_control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Save
import compose.icons.feathericons.Trash
import kotlinx.coroutines.launch
import proj.ankichess.axl.core.impl.graph.nodes.NodeFactory
import proj.ankichess.axl.core.impl.interactions.InteractionManager
import proj.ankichess.axl.core.intf.data.ICommonDataBase
import proj.ankichess.axl.core.intf.data.getCommonDataBase
import proj.ankichess.axl.ui.components.board.Board
import proj.ankichess.axl.ui.components.loading.LoadingPage
import proj.ankichess.axl.ui.util.impl.BasicReloader

@Composable
fun ControllableBoardPage(
  modifier: Modifier = Modifier,
  dataBase: ICommonDataBase = getCommonDataBase(),
) {
  LoadingPage({ NodeFactory.retrieveGraphFromDatabase(dataBase) }) { ControllableBoard(modifier) }
}

@Composable
private fun ControllableBoard(modifier: Modifier = Modifier) {
  var inverted by remember { mutableStateOf(false) }
  val boardReloader = remember { BasicReloader() }
  val interactionManager = remember { InteractionManager() }
  val coroutineScope = rememberCoroutineScope()

  val nextMoves = remember(boardReloader.getKey()) { interactionManager.getChildrenMoves() }
  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    modifier = modifier.padding(16.dp),
  ) {
    ControlBar(
      modifier = Modifier.height(50.dp),
      onReverseClick = { inverted = !inverted },
      onResetClick = { interactionManager.reset(boardReloader) },
      onForwardClick = { interactionManager.forward(boardReloader) },
      onBackClick = { interactionManager.back(boardReloader) },
    )
    Board(inverted, interactionManager, boardReloader, modifier = modifier.fillMaxWidth())
    NextMoveBar(moveList = nextMoves, playMove = { interactionManager.playMove(it, boardReloader) })
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Button(
        onClick = { coroutineScope.launch { interactionManager.save() } },
        modifier = Modifier.weight(1f),
      ) {
        Icon(FeatherIcons.Save, contentDescription = "Save")
      }
      Button(
        onClick = { coroutineScope.launch { interactionManager.delete(boardReloader) } },
        colors = androidx.compose.material.ButtonDefaults.buttonColors(backgroundColor = Color.Red),
        modifier = Modifier.weight(1f),
      ) {
        Icon(FeatherIcons.Trash, contentDescription = "Delete")
      }
    }
  }
}
