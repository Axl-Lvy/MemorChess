package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Save
import compose.icons.feathericons.Trash
import kotlinx.coroutines.launch
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.description_board_next_move
import org.jetbrains.compose.resources.stringResource
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
import proj.memorchess.axl.ui.layout.explore.ExploreLayoutContent
import proj.memorchess.axl.ui.layout.explore.LandscapeExploreLayout
import proj.memorchess.axl.ui.layout.explore.PortraitExploreLayout
import proj.memorchess.axl.ui.pages.navigation.Route

@Composable
fun Explore(position: PositionIdentifier? = null) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .padding(horizontal = 2.dp, vertical = 8.dp)
        .testTag(Route.ExploreRoute.DEFAULT.getLabel()),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    LoadingWidget({ NodeManager.resetCacheFromDataBase() }) {
      val modifier = Modifier.fillMaxWidth()
      var inverted by remember { mutableStateOf(false) }
      val linesExplorer = remember { LinesExplorer() }
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
              onClick = { coroutineScope.launch { linesExplorer.delete() } },
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
