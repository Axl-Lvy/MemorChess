package proj.memorchess.axl.ui.components.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import proj.memorchess.axl.core.config.ALL_SETTINGS_ITEMS
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography

/**
 * Danger Zone settings section content. Renders two irreversible actions: reset every persisted
 * preference to its default value, and erase the entire opening tree.
 *
 * Each action stacks the description above the action button so long captions ("Cannot be undone")
 * stay readable on narrow screens rather than wrapping into a 50% sub-column next to the button.
 * Buttons span the full width.
 *
 * Each action is gated by a [ConfirmationDialog]; the user must confirm before the underlying call
 * fires. Test tags `resetConfigButton` and `eraseAllDataButton` are preserved.
 *
 * @param treeStore Used to perform the "erase all data" action.
 * @param onReset Called after the user confirms the reset; the parent typically triggers a reload
 *   so the rest of the settings page reflects the restored defaults.
 */
@Composable
fun DangerZoneSection(treeStore: TreeStore = koinInject(), onReset: () -> Unit = {}) {
  val coroutineScope = rememberCoroutineScope()
  val dlg = remember { ConfirmationDialog() }
  dlg.DrawDialog()
  val palette = LocalKineticPalette.current

  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
    DangerRow(
      title = "Reset all settings to defaults",
      titleColor = palette.ink,
      description = "Your repertoire is untouched.",
      buttonLabel = "RESET",
      buttonStyle = KineticButtonStyle.DangerOutline,
      buttonTestTag = "resetConfigButton",
      onClick = {
        dlg.show("Are you sure you want to reset all settings?") {
          ALL_SETTINGS_ITEMS.forEach { it.reset() }
          onReset()
        }
      },
    )

    DangerRow(
      title = "Erase all data",
      titleColor = palette.red,
      description = "Wipes every position and every training record. Cannot be undone.",
      buttonLabel = "ERASE",
      buttonStyle = KineticButtonStyle.Danger,
      buttonTestTag = "eraseAllDataButton",
      onClick = {
        dlg.show("Are you sure you want to erase all data?") {
          coroutineScope.launch { treeStore.eraseAll() }
        }
      },
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
  }
}
