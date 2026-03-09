package proj.memorchess.axl.preview.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import compose.icons.FeatherIcons
import compose.icons.feathericons.Save
import compose.icons.feathericons.Trash
import org.koin.compose.koinInject
import proj.memorchess.axl.core.engine.ChessPiece
import proj.memorchess.axl.core.engine.PieceKind
import proj.memorchess.axl.core.engine.Player
import proj.memorchess.axl.core.graph.NodeState
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.ui.components.board.Board
import proj.memorchess.axl.ui.components.board.Piece
import proj.memorchess.axl.ui.components.board.StateIndicator
import proj.memorchess.axl.ui.components.buttons.ControlButton
import proj.memorchess.axl.ui.layout.explore.ExploreLayoutContent

internal val previewExploreLayoutContent =
  ExploreLayoutContent(
    resetButton = { ControlButton.RESET.render(it) {} },
    reverseButton = { ControlButton.REVERSE.render(it) {} },
    backButton = { ControlButton.BACK.render(it) {} },
    forwardButton = { ControlButton.FORWARD.render(it) {} },
    nextMoveButtons = {
      listOf("a4", "b3", "c3", "c4", "d3", "d4").map {
        {
          Box(
            modifier =
              Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable {},
            contentAlignment = Alignment.Center,
          ) {
            Text(it)
          }
        }
      }
    },
    stateIndicators = { StateIndicator(it, NodeState.FIRST) },
    evaluationBar = {},
    playerTurnIndicator = { Piece(ChessPiece(PieceKind.KING, Player.WHITE)) },
    saveButton = {
      Button(
        onClick = {},
        it,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
      ) {
        Icon(FeatherIcons.Save, contentDescription = "Save")
      }
    },
    deleteButton = {
      Button(
        onClick = {},
        it,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
      ) {
        Icon(FeatherIcons.Trash, contentDescription = "Delete")
      }
    },
    board = {
      val nodeManager: NodeManager = koinInject()
      Board(inverted = false, interactionsManager = LinesExplorer(nodeManager = nodeManager))
    },
  )
