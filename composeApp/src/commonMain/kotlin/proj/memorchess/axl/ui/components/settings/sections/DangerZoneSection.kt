package proj.memorchess.axl.ui.components.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.settings_erase_button
import memorchess.composeapp.generated.resources.settings_erase_confirm
import memorchess.composeapp.generated.resources.settings_erase_description
import memorchess.composeapp.generated.resources.settings_erase_failed
import memorchess.composeapp.generated.resources.settings_erase_sign_in_required
import memorchess.composeapp.generated.resources.settings_erase_title
import memorchess.composeapp.generated.resources.settings_reset_button
import memorchess.composeapp.generated.resources.settings_reset_confirm
import memorchess.composeapp.generated.resources.settings_reset_description
import memorchess.composeapp.generated.resources.settings_reset_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import proj.memorchess.axl.core.config.ALL_SETTINGS_ITEMS
import proj.memorchess.axl.core.streak.StreakTracker
import proj.memorchess.axl.core.sync.EraseOutcome
import proj.memorchess.axl.core.sync.SyncEngine
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * Danger Zone settings section content. Renders two irreversible actions: reset every persisted
 * preference to its default value, and erase all data on this device and, when signed in, on every
 * device synced to the account.
 *
 * Each action stacks the description above the action button so long captions ("Cannot be undone")
 * stay readable on narrow screens rather than wrapping into a 50% sub-column next to the button.
 * Buttons span the full width.
 *
 * Each action is gated by a [ConfirmationDialog]; the user must confirm before the underlying call
 * fires. Test tags `resetConfigButton` and `eraseAllDataButton` are preserved.
 *
 * @param syncEngine Performs the "erase all data" action; a failed erase shows an error under the
 *   erase row and deletes nothing.
 * @param streakTracker Also wiped once the erase succeeds, so the streak and daily count go with
 *   the rest of the training history.
 * @param onReset Called after the user confirms the reset; the parent typically triggers a reload
 *   so the rest of the settings page reflects the restored defaults.
 */
@Composable
fun DangerZoneSection(
  syncEngine: SyncEngine = koinInject(),
  streakTracker: StreakTracker = koinInject(),
  onReset: () -> Unit,
) {
  val coroutineScope = rememberCoroutineScope()
  val dlg = remember { ConfirmationDialog() }
  dlg.DrawDialog()
  val palette = LocalKineticPalette.current
  val resetConfirm = stringResource(Res.string.settings_reset_confirm)
  val eraseConfirm = stringResource(Res.string.settings_erase_confirm)
  var eraseOutcome: EraseOutcome? by remember { mutableStateOf(null) }
  val eraseError =
    when (eraseOutcome) {
      EraseOutcome.Failed -> stringResource(Res.string.settings_erase_failed)
      EraseOutcome.SignInRequired -> stringResource(Res.string.settings_erase_sign_in_required)
      EraseOutcome.Erased,
      null -> null
    }

  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
    DangerRow(
      title = stringResource(Res.string.settings_reset_title),
      titleColor = palette.ink,
      description = stringResource(Res.string.settings_reset_description),
      buttonLabel = stringResource(Res.string.settings_reset_button),
      buttonStyle = KineticButtonStyle.DangerOutline,
      buttonTestTag = "resetConfigButton",
      onClick = {
        dlg.show(resetConfirm) {
          ALL_SETTINGS_ITEMS.forEach { it.reset() }
          onReset()
        }
      },
    )

    DangerRow(
      title = stringResource(Res.string.settings_erase_title),
      titleColor = palette.destructive,
      description = stringResource(Res.string.settings_erase_description),
      buttonLabel = stringResource(Res.string.settings_erase_button),
      buttonStyle = KineticButtonStyle.Danger,
      buttonTestTag = "eraseAllDataButton",
      onClick = {
        dlg.show(eraseConfirm) {
          coroutineScope.launch {
            val outcome = syncEngine.eraseEverywhere()
            if (outcome == EraseOutcome.Erased) {
              streakTracker.eraseAll()
            }
            eraseOutcome = outcome
          }
        }
      },
      error = eraseError,
    )
  }
}

/**
 * One Danger-Zone action: vertical stack of title + description, followed by a full-width Kinetic
 * button. The vertical layout keeps the long "Cannot be undone." caption from being squashed into a
 * narrow column on phones.
 */
@Composable
private fun DangerRow(
  title: String,
  titleColor: androidx.compose.ui.graphics.Color,
  description: String,
  buttonLabel: String,
  buttonStyle: KineticButtonStyle,
  buttonTestTag: String,
  onClick: () -> Unit,
  error: String? = null,
) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalAlignment = Alignment.Start,
  ) {
    Text(text = title, style = typography.display.copy(color = titleColor))
    Text(
      text = description,
      style = typography.bodySm.copy(color = palette.ink3),
      textAlign = TextAlign.Start,
      modifier = Modifier.fillMaxWidth(),
    )
    KineticButton(
      onClick = onClick,
      style = buttonStyle,
      modifier = Modifier.fillMaxWidth().testTag(buttonTestTag),
    ) {
      Text(text = buttonLabel)
    }
    if (error != null) {
      Text(
        text = error,
        style = typography.bodySm.copy(color = palette.destructive),
        modifier = Modifier.testTag("erase_error"),
      )
    }
  }
}
