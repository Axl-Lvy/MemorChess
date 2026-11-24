package proj.memorchess.axl.ui.components.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.dialog_cancel
import memorchess.composeapp.generated.resources.dialog_delete
import memorchess.composeapp.generated.resources.explore_prune_continue
import memorchess.composeapp.generated.resources.explore_prune_positions
import memorchess.composeapp.generated.resources.explore_prune_positions_many
import memorchess.composeapp.generated.resources.explore_prune_reviewed
import memorchess.composeapp.generated.resources.explore_prune_reviewed_at_least
import memorchess.composeapp.generated.resources.explore_prune_reviewed_none
import memorchess.composeapp.generated.resources.explore_prune_select_body
import memorchess.composeapp.generated.resources.explore_prune_select_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.data.DESCENDANT_COUNT_CAP
import proj.memorchess.axl.core.data.PruneImpact
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.components.popup.KineticBottomSheet
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/** Step of the two step branch prune flow. */
internal enum class PruneStep(val sheetTag: String) {
  /** The branch is highlighted behind the scrim. Nothing is deleted yet. */
  SELECTING("pruneSelectSheet"),
  /** The sheet states what the delete removes and waits for the final tap. */
  CONFIRMING("pruneConfirmSheet"),
}

/**
 * Bottom sheet of the branch prune flow. [PruneStep.SELECTING] offers to continue with the
 * highlighted branch; [PruneStep.CONFIRMING] states the loss and offers the delete.
 *
 * @param impact What the delete removes, or `null` while it is still measured.
 */
@Composable
internal fun ExplorePruneSheet(
  visible: Boolean,
  step: PruneStep,
  impact: PruneImpact?,
  onContinue: () -> Unit,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  KineticBottomSheet(
    visible = visible,
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag(step.sheetTag),
    buttons = {
      KineticButton(
        onClick = onDismiss,
        style = KineticButtonStyle.Ghost,
        modifier = Modifier.testTag("${step.sheetTag}CancelButton"),
      ) {
        Text(stringResource(Res.string.dialog_cancel))
      }
      when (step) {
        PruneStep.SELECTING ->
          KineticButton(
            onClick = onContinue,
            style = KineticButtonStyle.Primary,
            modifier = Modifier.testTag("pruneSelectSheetContinueButton"),
          ) {
            Text(stringResource(Res.string.explore_prune_continue))
          }
        PruneStep.CONFIRMING ->
          KineticButton(
            onClick = onConfirm,
            style = KineticButtonStyle.Danger,
            modifier = Modifier.testTag("pruneConfirmSheetOkButton"),
          ) {
            Text(stringResource(Res.string.dialog_delete))
          }
      }
    },
  ) {
    when (step) {
      PruneStep.SELECTING -> SelectingContent()
      PruneStep.CONFIRMING -> ConfirmingContent(impact)
    }
  }
}

@Composable
private fun SelectingContent() {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = stringResource(Res.string.explore_prune_select_title),
      style = typography.display.copy(color = palette.ink),
    )
    Text(
      text = stringResource(Res.string.explore_prune_select_body),
      style = typography.body.copy(color = palette.ink2),
    )
  }
}

@Composable
private fun ConfirmingContent(impact: PruneImpact?) {
  if (impact == null) {
    CircularProgressIndicator()
    return
  }
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = positionsHeadline(impact),
      style = typography.displayLg.copy(color = palette.destructiveText),
    )
    reviewedLine(impact)?.let {
      Text(text = it, style = typography.body.copy(color = palette.ink2))
    }
  }
}

/** Headline stating how many positions the delete removes. */
@Composable
private fun positionsHeadline(impact: PruneImpact): String =
  if (impact.positions >= DESCENDANT_COUNT_CAP) {
    // The walk stops at the cap, so at the cap only "this many or more" is known.
    stringResource(Res.string.explore_prune_positions_many, DESCENDANT_COUNT_CAP)
  } else {
    pluralStringResource(Res.plurals.explore_prune_positions, impact.positions, impact.positions)
  }

/**
 * Line stating how many removed positions carry review history, or `null` at the cap with none
 * found, since positions past the cap were never inspected.
 */
@Composable
private fun reviewedLine(impact: PruneImpact): String? {
  val reviewed = impact.reviewedPositions
  val capped = impact.positions >= DESCENDANT_COUNT_CAP
  return when {
    reviewed == 0 && capped -> null
    reviewed == 0 -> stringResource(Res.string.explore_prune_reviewed_none)
    capped -> pluralStringResource(Res.plurals.explore_prune_reviewed_at_least, reviewed, reviewed)
    else -> pluralStringResource(Res.plurals.explore_prune_reviewed, reviewed, reviewed)
  }
}
