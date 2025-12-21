package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.description_board_next_move
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.vectors.King
import proj.memorchess.axl.core.graph.nodes.Node
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.ui.components.board.Board
import proj.memorchess.axl.ui.components.board.Piece
import proj.memorchess.axl.ui.components.board.StateIndicator
import proj.memorchess.axl.ui.components.buttons.ControlButton
import proj.memorchess.axl.ui.layout.explore.ExploreLayoutContent
import proj.memorchess.axl.ui.layout.explore.LandscapeExploreLayout
import proj.memorchess.axl.ui.layout.explore.PortraitExploreLayout

/**
 * Shared explorer content relying on a [LinesExplorer].
 *
 * @param explorer The explorer instance (LinesExplorer or BookExplorer).
 * @param saveButton Custom composable for the save button (can be null if not applicable).
 * @param deleteButton Custom composable for the delete button (can be null if not applicable).
 * @param header Optional header content to display above the explorer.
 */
@Composable
fun <NodeT : Node<NodeT>> ExplorerContent(
  explorer: LinesExplorer<NodeT>,
  saveButton: @Composable (Modifier) -> Unit,
  deleteButton: @Composable (Modifier) -> Unit,
  header: @Composable () -> Unit = {},
) {
  val modifier = Modifier.fillMaxWidth()
  var inverted by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()
  val nextMoves = remember { mutableStateListOf(*explorer.getNextMoves().toTypedArray()) }

  remember {
    explorer.registerCallBack {
      nextMoves.clear()
      nextMoves.addAll(explorer.getNextMoves())
    }
  }

  val content = remember {
    ExploreLayoutContent(
      resetButton = { ControlButton.RESET.render(it) { explorer.reset() } },
      reverseButton = { ControlButton.REVERSE.render(it) { inverted = !inverted } },
      backButton = { ControlButton.BACK.render(it) { explorer.back() } },
      forwardButton = { ControlButton.FORWARD.render(it) { explorer.forward() } },
      nextMoveButtons = {
        nextMoves.map<String, @Composable (() -> Unit)> {
          { NextMoveButton(it) { coroutineScope.launch { explorer.playMove(it) } } }
        }
      },
      playerTurnIndicator = {
        var playerTurn by remember {
          mutableStateOf(explorer.game.position.playerTurn == Game.Player.WHITE)
        }
        explorer.registerCallBack {
          playerTurn = explorer.game.position.playerTurn == Game.Player.WHITE
        }
        Piece(if (playerTurn) King.white() else King.black())
      },
      stateIndicators = { StateIndicator(it, explorer.state) },
      saveButton = saveButton,
      deleteButton = deleteButton,
      board = { Board(inverted, explorer, it) },
    )
  }

  header()

  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    if (maxHeight > maxWidth) PortraitExploreLayout(modifier, content)
    else LandscapeExploreLayout(modifier, content)
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
