package proj.memorchess.axl.ui.components.board.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import memorchess.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.engine.Game
import proj.memorchess.axl.core.engine.pieces.vectors.King
import proj.memorchess.axl.ui.components.board.Piece

@Composable
fun ControlBar(
  onReverseClick: () -> Unit,
  onResetClick: () -> Unit,
  onBackClick: () -> Unit,
  onForwardClick: () -> Unit,
  playerTurn: Game.Player,
  modifier: Modifier = Modifier,
) {
  Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = modifier.fillMaxWidth()) {
    ControlButton.REVERSE.render(
      onReverseClick,
      modifier = Modifier.size(SIZE).testTag(stringResource(Res.string.description_board_reverse)),
    )
    ControlButton.RESET.render(
      onResetClick,
      modifier = Modifier.size(SIZE).testTag(stringResource(Res.string.description_board_reset)),
    )
    Piece(if (playerTurn == Game.Player.WHITE) King.white() else King.black())
    ControlButton.BACK.render(
      onBackClick,
      modifier = Modifier.size(SIZE).testTag(stringResource(Res.string.description_board_back)),
    )
    ControlButton.FORWARD.render(
      onForwardClick,
      modifier = Modifier.size(SIZE).testTag(stringResource(Res.string.description_board_next)),
    )
  }
}

private val SIZE = 50.dp
