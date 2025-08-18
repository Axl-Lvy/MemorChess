package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import proj.memorchess.axl.core.stockfish.StockfishEvaluator
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
      val evaluator = remember { StockfishEvaluator() }
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
          coroutineScope.launch {
            evaluator.evaluate(linesExplorer.game.position.createIdentifier())
          }
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
          boardTopping = {
            Row(it) {
              StateIndicator(Modifier.weight(1f), linesExplorer.state)
              Spacer(Modifier.width(2.dp))
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(4.dp)),
              ) {
                val eval by evaluator.evaluation.collectAsState()
                EvaluationBar(eval = eval, modifier = Modifier.fillMaxSize())
              }
            }
          },
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

@Composable
private fun EvaluationBar(
  eval: String,
  modifier: Modifier = Modifier,
  maxEval: Float = 5f, // Clamp evaluation to [-5, 5]
) {
  // Adjust for player/inverted
  val percentWhite =
    if (eval.contains("M")) {
      if (eval.startsWith("M")) {
        // Mate in favor of white
        1f
      } else if (eval.startsWith("-M")) {
        // Mate in favor of black
        0f
      } else {
        // Invalid mate evaluation
        0.5f
      }
    } else {
      val numericEval = eval.toFloatOrNull() ?: 0f
      ((numericEval.coerceIn(-maxEval, maxEval) + maxEval) / (2 * maxEval)).coerceIn(0f, 1f)
    }

  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Row(modifier = Modifier.fillMaxSize()) {
      if (percentWhite > 0f) {
        Box(
          modifier =
            Modifier.weight(percentWhite)
              .fillMaxSize()
              .background(Color.White.copy(0.5f)) // White section
        )
      }
      if (1f - percentWhite > 0f) {
        Box(
          modifier =
            Modifier.weight(1f - percentWhite)
              .fillMaxSize()
              .background(Color.Black.copy(0.5f)) // Black section
        )
      }
    }
    // Overlay the numeric value
    Text(
      text = eval,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.align(Alignment.Center),
    )
  }
}
