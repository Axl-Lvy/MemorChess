package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import memorchess.composeapp.generated.resources.explore_save
import memorchess.composeapp.generated.resources.explore_white
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonLabel
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * Callbacks for the [ExploreCtrlBar] buttons.
 *
 * @property onReverse Flips the board.
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
 * Narrowest bar that fits a labeled Save; below it the [Row] would shrink Delete to nothing. Sits
 * above the measured minimums, 406.dp in English and 458.dp in French.
 */
private val SAVE_LABEL_MIN_WIDTH = 500.dp

/**
 * Explore control bar of icon buttons around a turn pill. Save is labeled only when the bar is at
 * least [SAVE_LABEL_MIN_WIDTH] wide.
 */
@Composable
fun ExploreCtrlBar(
  actions: ExploreCtrlBarActions,
  evalEnabled: Boolean,
  playerTurnWhite: Boolean,
  modifier: Modifier = Modifier,
  showSaveDelete: Boolean = true,
) {
  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    ExploreCtrlBarRow(
      actions = actions,
      evalEnabled = evalEnabled,
      playerTurnWhite = playerTurnWhite,
      showSaveDelete = showSaveDelete,
      showSaveLabel = maxWidth >= SAVE_LABEL_MIN_WIDTH,
    )
  }
}

/** The bar's actual content row, split out so [ExploreCtrlBar] can measure width above it. */
@Composable
private fun ExploreCtrlBarRow(
  actions: ExploreCtrlBarActions,
  evalEnabled: Boolean,
  playerTurnWhite: Boolean,
  showSaveDelete: Boolean,
  showSaveLabel: Boolean,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  val pillShape = MaterialTheme.shapes.extraSmall

  Row(
    modifier = Modifier.fillMaxWidth(),
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

    // No clip: the only child is a Text inset by the padding below, so nothing reaches a corner.
    Box(
      modifier =
        Modifier.background(palette.panel2, pillShape)
          .border(width = 1.5.dp, color = palette.line, shape = pillShape)
          .padding(horizontal = 10.dp, vertical = 8.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text =
          if (playerTurnWhite) stringResource(Res.string.explore_white)
          else stringResource(Res.string.explore_black),
        style = typography.labelSm.copy(color = palette.ink2),
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

    Box(modifier = Modifier.weight(1f).fillMaxWidth())

    if (showSaveDelete) {
      KineticButton(
        onClick = actions.onSave,
        style = KineticButtonStyle.Primary,
        iconOnly = !showSaveLabel,
      ) {
        Icon(
          FeatherIcons.Save,
          contentDescription = stringResource(Res.string.description_board_save),
        )
        if (showSaveLabel) {
          KineticButtonLabel(stringResource(Res.string.explore_save))
        }
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
