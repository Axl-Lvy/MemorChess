package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.diamondedge.logging.logging
import compose.icons.FeatherIcons
import compose.icons.feathericons.Save
import compose.icons.feathericons.Trash
import kotlinx.coroutines.launch
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.description_board_next_move
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.PositionIdentifier
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.vectors.King
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.ui.components.board.Board
import proj.memorchess.axl.ui.components.board.Piece
import proj.memorchess.axl.ui.components.board.StateIndicator
import proj.memorchess.axl.ui.components.buttons.ControlButton
import proj.memorchess.axl.ui.components.loading.LoadingWidget
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.layout.explore.ExploreLayoutContent
import proj.memorchess.axl.ui.layout.explore.LandscapeExploreLayout
import proj.memorchess.axl.ui.layout.explore.PortraitExploreLayout
import proj.memorchess.axl.ui.pages.navigation.Route

private val LOGGER = logging()

@Composable
fun Explore(position: PositionIdentifier? = null, nodeManager: NodeManager = koinInject()) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .padding(horizontal = 2.dp, vertical = 8.dp)
        .testTag(Route.ExploreRoute.DEFAULT.getLabel()),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    LoadingWidget({ nodeManager.resetCacheFromDataBase() }) {
      val initialPosition = extractInitialPosition(position, nodeManager)
      val modifier = Modifier.fillMaxWidth()
      var inverted by remember { mutableStateOf(false) }
      val linesExplorer = remember { LinesExplorer(initialPosition) }
      val coroutineScope = rememberCoroutineScope()
      val nextMoves = remember {
        mutableStateListOf(*linesExplorer.getNextMoves().toTypedArray<String>())
      }
      remember {
        linesExplorer.registerCallBack {
          nextMoves.clear()
          nextMoves.addAll(linesExplorer.getNextMoves())
        }
      }
      val deletionConfirmationDialog = remember { ConfirmationDialog(okText = "Delete") }
      deletionConfirmationDialog.DrawDialog()
      val content = remember {
        ExploreLayoutContent(
          resetButton = { ControlButton.RESET.render(it) { linesExplorer.reset() } },
          reverseButton = { ControlButton.REVERSE.render(it) { inverted = !inverted } },
          backButton = { ControlButton.BACK.render(it) { linesExplorer.back() } },
          forwardButton = { ControlButton.FORWARD.render(it) { linesExplorer.forward() } },
          nextMoveButtons = {
            nextMoves.map<String, @Composable (() -> Unit)> {
              { NextMoveButton(it) { coroutineScope.launch { linesExplorer.playMove(it) } } }
            }
          },
          playerTurnIndicator = {
            var playerTurn by remember {
              mutableStateOf(linesExplorer.game.position.playerTurn == Game.Player.WHITE)
            }
            linesExplorer.registerCallBack {
              playerTurn = linesExplorer.game.position.playerTurn == Game.Player.WHITE
            }
            Piece(if (playerTurn) King.white() else King.black())
          },
          stateIndicators = { StateIndicator(it, linesExplorer.state) },
          saveButton = {
            Button(
              onClick = { coroutineScope.launch { linesExplorer.save() } },
              it,
              colors =
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
              Icon(FeatherIcons.Save, contentDescription = "Save")
            }
          },
          deleteButton = {
            Button(
              onClick = {
                deletionConfirmationDialog.show(
                  confirm = { coroutineScope.launch { linesExplorer.delete() } }
                ) {
                  var nodesToDelete by remember { mutableStateOf<Int?>(null) }
                  if (nodesToDelete == null) {
                    CircularProgressIndicator()
                  } else {
                    val finalNodesToDelete = nodesToDelete ?: 0
                    Text(
                      "Are you sure you want to delete $finalNodesToDelete position${if (finalNodesToDelete > 1) "s" else ""}?"
                    )
                  }
                  LaunchedEffect(nodesToDelete) {
                    nodesToDelete = linesExplorer.calculateNumberOfNodeToDelete()
                  }
                }
              },
              it,
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
              Icon(FeatherIcons.Trash, contentDescription = "Delete")
            }
          },
          board = { Board(inverted, linesExplorer, it) },
        )
      }
      BoxWithConstraints {
        if (maxHeight > maxWidth) PortraitExploreLayout(modifier, content)
        else LandscapeExploreLayout(modifier, content)
      }
    }
  }
}

private fun extractInitialPosition(
  position: PositionIdentifier?,
  nodeManager: NodeManager,
): PositionIdentifier? {
  println("Extracting initial position: $position")
  return if (position == null) {
    null
  } else if (!nodeManager.isKnown(position)) {
    LOGGER.warn {
      "Position $position is not stored yet. You must first store it to integrate it in your position tree."
    }
    null
  } else {
    position
  }
}

@Composable
private fun NextMoveButton(move: String, playMove: () -> Unit) {
  Box(
    modifier =
      Modifier.fillMaxSize()
        .testTag(stringResource(Res.string.description_board_next_move, move))
        .clickable { playMove() },
    contentAlignment = Alignment.Center,
  ) {
    Text(move)
  }
}
