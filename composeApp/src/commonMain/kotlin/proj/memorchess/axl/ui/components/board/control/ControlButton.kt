package proj.memorchess.axl.ui.components.board.control

import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.ArrowRight
import compose.icons.feathericons.Repeat
import compose.icons.feathericons.Rewind
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.description_board_back
import memorchess.composeapp.generated.resources.description_board_next
import memorchess.composeapp.generated.resources.description_board_reset
import memorchess.composeapp.generated.resources.description_board_reverse
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class ControlButton(
  private val icon: ImageVector,
  private val contentDescription: StringResource,
) {
  REVERSE(icon = FeatherIcons.Repeat, contentDescription = Res.string.description_board_reverse),
  RESET(icon = FeatherIcons.Rewind, contentDescription = Res.string.description_board_reset),
  BACK(icon = FeatherIcons.ArrowLeft, contentDescription = Res.string.description_board_back),
  FORWARD(icon = FeatherIcons.ArrowRight, contentDescription = Res.string.description_board_next);

  @Composable
  fun render(modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalIconButton(
      onClick = onClick,
      modifier = modifier.testTag(stringResource(contentDescription)),
    ) {
      Icon(imageVector = icon, contentDescription = stringResource(contentDescription))
    }
  }
}
