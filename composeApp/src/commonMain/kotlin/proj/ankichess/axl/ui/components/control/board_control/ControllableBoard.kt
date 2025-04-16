package proj.ankichess.axl.ui.components.control.board_control

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Save
import kotlinx.coroutines.launch
import proj.ankichess.axl.core.impl.graph.nodes.NodeFactory
import proj.ankichess.axl.core.impl.interactions.InteractionManager
import proj.ankichess.axl.ui.components.board.Board
import proj.ankichess.axl.ui.util.impl.BasicReloader

@Composable
fun ControllableBoard(modifier: Modifier = Modifier) {
  var inverted by remember { mutableStateOf(false) }
  val boardReloader = remember { BasicReloader() }
  val interactionManager = remember { InteractionManager() }
  val nextMoves = remember(boardReloader.getKey()) { interactionManager.getChildrenMoves() }
  NodeFactory.retrieveGraphFromDatabase()
  val coroutineScope = rememberCoroutineScope()
  Column(verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
    ControlBar(
      modifier = Modifier.height(50.dp),
      onReverseClick = { inverted = !inverted },
      onResetClick = { interactionManager.reset(boardReloader) },
      onForwardClick = { interactionManager.forward(boardReloader) },
      onBackClick = { interactionManager.back(boardReloader) },
    )
    Board(inverted, interactionManager, boardReloader, modifier = modifier.fillMaxWidth())
    NextMoveBar(moveList = nextMoves, playMove = { interactionManager.playMove(it, boardReloader) })
    Button(onClick = { coroutineScope.launch { interactionManager.save() } }) {
      Icon(FeatherIcons.Save, contentDescription = "Save")
    }
  }
}
