package proj.memorchess.axl.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import compose.icons.FeatherIcons
import compose.icons.feathericons.Save
import compose.icons.feathericons.Trash
import proj.memorchess.axl.core.engine.pieces.vectors.King
import proj.memorchess.axl.core.graph.nodes.Node
import proj.memorchess.axl.core.interactions.LinesExplorer
import proj.memorchess.axl.ui.components.board.Board
import proj.memorchess.axl.ui.components.board.Piece
import proj.memorchess.axl.ui.components.board.StateIndicator
import proj.memorchess.axl.ui.components.buttons.ControlButton
import proj.memorchess.axl.ui.layout.explore.ExploreLayoutContent

val previewExploreLayoutContent =
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
    stateIndicators = { StateIndicator(it, Node.NodeState.FIRST) },
    playerTurnIndicator = { Piece(King.white()) },
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
    board = { Board(inverted = false, interactionsManager = LinesExplorer()) },
  )
