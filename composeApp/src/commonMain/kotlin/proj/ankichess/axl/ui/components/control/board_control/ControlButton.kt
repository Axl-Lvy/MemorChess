package proj.ankichess.axl.ui.components.control.board_control

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.ArrowRight
import compose.icons.feathericons.Repeat
import compose.icons.feathericons.Rewind

enum class ControlButton(private val contentDescription: String, private val icon: ImageVector) {
  REVERSE(contentDescription = "Reverse", icon = FeatherIcons.Repeat),
  RESET(contentDescription = "Reset", icon = FeatherIcons.Rewind),
  BACK(contentDescription = "Back", icon = FeatherIcons.ArrowLeft),
  FORWARD(contentDescription = "Forward", icon = FeatherIcons.ArrowRight);

  @Composable
  fun render(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, contentPadding = PaddingValues(2.dp), modifier = modifier) {
      Icon(imageVector = icon, contentDescription = contentDescription)
    }
  }
}
