package proj.memorchess.axl.ui.components.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
  val typography = LocalKineticTypography.current

  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Reset all settings to defaults",
          style = typography.display.copy(color = palette.ink),
        )
        Text(
          text = "Your repertoire is untouched.",
          style = typography.bodySm.copy(color = palette.ink3),
        )
      }
      KineticButton(
        onClick = {
          dlg.show("Are you sure you want to reset all settings?") {
            ALL_SETTINGS_ITEMS.forEach { it.reset() }
            onReset()
          }
        },
        style = KineticButtonStyle.DangerOutline,
        modifier = Modifier.testTag("resetConfigButton"),
      ) {
        Text(text = "RESET")
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(text = "Erase all data", style = typography.display.copy(color = palette.red))
        Text(
          text = "Wipes every position and every training record. Cannot be undone.",
          style = typography.bodySm.copy(color = palette.ink3),
        )
      }
      KineticButton(
        onClick = {
          dlg.show("Are you sure you want to erase all data?") {
            coroutineScope.launch { treeStore.eraseAll() }
          }
        },
        style = KineticButtonStyle.Danger,
        modifier = Modifier.testTag("eraseAllDataButton"),
      ) {
        Text(text = "ERASE")
      }
    }
  }
}
