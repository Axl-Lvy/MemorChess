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
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.description_board_back
import memorchess.composeapp.generated.resources.description_board_delete
import memorchess.composeapp.generated.resources.description_board_save
import memorchess.composeapp.generated.resources.description_forward
import memorchess.composeapp.generated.resources.description_reset
import memorchess.composeapp.generated.resources.description_reverse
import memorchess.composeapp.generated.resources.description_toggle_eval_bar
import memorchess.composeapp.generated.resources.explore_black
import memorchess.composeapp.generated.resources.explore_white
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * Callbacks for the [ExploreCtrlBar] buttons.
 *
 * @property onReset Invoked when the reset button is tapped.
 * @property onReverse Invoked when the reverse (flip board) button is tapped.
 * @property onBack Invoked when the back arrow is tapped.
 * @property onForward Invoked when the forward arrow is tapped.
 * @property onToggleEval Invoked when the eval-bar toggle is tapped.
 * @property onSave Invoked when the save button is tapped (Primary style).
 * @property onDelete Invoked when the delete button is tapped (Danger style).
 */
data class ExploreCtrlBarActions(
  val onReset: () -> Unit,
  val onReverse: () -> Unit,
  val onBack: () -> Unit,
  val onForward: () -> Unit,
  val onToggleEval: () -> Unit,
  val onSave: () -> Unit,
  val onDelete: () -> Unit,
)

/**
 * Compact Kinetic control bar replacing the loose reset / reverse / back / forward / eval-toggle /
 * save / delete row. Each control uses an icon-only [KineticButton]; the player turn indicator sits
 * mid-row as a small panel pill showing whose move it is.
 *
 * @param actions Callbacks for each button.
 * @param evalEnabled `true` if the eval bar is currently enabled (drives Primary vs Default style).
 * @param playerTurnWhite `true` when it is white's move, used for the central player-turn pill.
 * @param modifier External modifier applied to the row.
 * @param showSaveDelete `true` to render the save and delete buttons; `false` hides them for read
 *   only contexts such as the repertoire viewer.
 */
@Composable
fun ExploreCtrlBar(
  actions: ExploreCtrlBarActions,
  evalEnabled: Boolean,
  playerTurnWhite: Boolean,
  modifier: Modifier = Modifier,
  showSaveDelete: Boolean = true,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current

  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    KineticButton(onClick = actions.onReset, iconOnly = true) {
      Icon(FeatherIcons.Rewind, contentDescription = stringResource(Res.string.description_reset))
    }
    KineticButton(onClick = actions.onReverse, iconOnly = true) {
      Icon(FeatherIcons.Repeat, contentDescription = stringResource(Res.string.description_reverse))
    }
    KineticButton(onClick = actions.onBack, iconOnly = true) {
      Icon(
        FeatherIcons.ArrowLeft,
        contentDescription = stringResource(Res.string.description_board_back),
      )
    }
    KineticButton(onClick = actions.onForward, iconOnly = true) {
      Icon(
        FeatherIcons.ArrowRight,
        contentDescription = stringResource(Res.string.description_forward),
      )
    }

    Box(
      modifier =
        Modifier.background(palette.panel2)
          .border(width = 1.dp, color = palette.line)
          .padding(horizontal = 10.dp, vertical = 8.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text =
          if (playerTurnWhite) stringResource(Res.string.explore_white)
          else stringResource(Res.string.explore_black),
        style = typography.monoSm.copy(color = palette.ink2),
      )
    }

    KineticButton(
      onClick = actions.onToggleEval,
      iconOnly = true,
      style = if (evalEnabled) KineticButtonStyle.Primary else KineticButtonStyle.Default,
    ) {
      Icon(
        FeatherIcons.BarChart2,
        contentDescription = stringResource(Res.string.description_toggle_eval_bar),
      )
    }

    // Filler — pushes save/delete to the right.
    Box(modifier = Modifier.weight(1f).fillMaxWidth())

    if (showSaveDelete) {
      KineticButton(onClick = actions.onSave, style = KineticButtonStyle.Primary, iconOnly = true) {
        Icon(
          FeatherIcons.Save,
          contentDescription = stringResource(Res.string.description_board_save),
        )
      }
      KineticButton(
        onClick = actions.onDelete,
        style = KineticButtonStyle.Danger,
        iconOnly = true,
      ) {
        Icon(
          FeatherIcons.Trash,
          contentDescription = stringResource(Res.string.description_board_delete),
        )
      }
    }
  }
}
