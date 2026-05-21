package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.ArrowRight
import compose.icons.feathericons.BarChart2
import compose.icons.feathericons.Repeat
import compose.icons.feathericons.Rewind
import compose.icons.feathericons.Save
import compose.icons.feathericons.Trash
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * Compact Kinetic control bar replacing the loose reset / reverse / back / forward / eval-toggle /
 * save / delete row. Each control uses an icon-only [KineticButton]; the player turn indicator sits
 * mid-row as a small panel pill showing whose move it is.
 *
 * @param onReset Invoked when the reset button is tapped.
 * @param onReverse Invoked when the reverse (flip board) button is tapped.
 * @param onBack Invoked when the back arrow is tapped.
 * @param onForward Invoked when the forward arrow is tapped.
 * @param onToggleEval Invoked when the eval-bar toggle is tapped.
 * @param evalEnabled `true` if the eval bar is currently enabled (drives Primary vs Default style).
 * @param onSave Invoked when the save button is tapped (Primary style).
 * @param onDelete Invoked when the delete button is tapped (Danger style).
 * @param playerTurnWhite `true` when it is white's move, used for the central player-turn pill.
 */
@Composable
fun ExploreCtrlBar(
  onReset: () -> Unit,
  onReverse: () -> Unit,
  onBack: () -> Unit,
  onForward: () -> Unit,
  onToggleEval: () -> Unit,
  evalEnabled: Boolean,
  onSave: () -> Unit,
  onDelete: () -> Unit,
  playerTurnWhite: Boolean,
  modifier: Modifier = Modifier,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current

  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    KineticButton(onClick = onReset, iconOnly = true) {
      Icon(FeatherIcons.Rewind, contentDescription = "Reset")
    }
    KineticButton(onClick = onReverse, iconOnly = true) {
      Icon(FeatherIcons.Repeat, contentDescription = "Reverse")
    }
    KineticButton(onClick = onBack, iconOnly = true) {
      Icon(FeatherIcons.ArrowLeft, contentDescription = "Back")
    }
    KineticButton(onClick = onForward, iconOnly = true) {
      Icon(FeatherIcons.ArrowRight, contentDescription = "Forward")
    }

    Box(
      modifier =
        Modifier.background(palette.panel2)
          .border(width = 1.dp, color = palette.line)
          .padding(horizontal = 10.dp, vertical = 8.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = if (playerTurnWhite) "WHITE" else "BLACK",
        style = typography.monoSm.copy(color = palette.ink2),
      )
    }

    KineticButton(
      onClick = onToggleEval,
      iconOnly = true,
      style = if (evalEnabled) KineticButtonStyle.Primary else KineticButtonStyle.Default,
    ) {
      Icon(FeatherIcons.BarChart2, contentDescription = "Toggle evaluation bar")
    }

    // Filler — pushes save/delete to the right.
    Box(modifier = Modifier.weight(1f).fillMaxWidth())

    KineticButton(onClick = onSave, style = KineticButtonStyle.Primary, iconOnly = true) {
      Icon(FeatherIcons.Save, contentDescription = "Save")
    }
    KineticButton(onClick = onDelete, style = KineticButtonStyle.Danger, iconOnly = true) {
      Icon(FeatherIcons.Trash, contentDescription = "Delete")
    }
  }
}
