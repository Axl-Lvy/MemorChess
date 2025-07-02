package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import proj.memorchess.axl.core.config.ALL_SETTINGS_ITEMS
import proj.memorchess.axl.core.data.DatabaseHolder.getDatabase
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.components.settings.EmbeddedSettingItem
import proj.memorchess.axl.ui.pages.navigation.Destination
import proj.memorchess.axl.ui.util.BasicReloader

@Composable
fun Settings() {
  val coroutineScope = rememberCoroutineScope()
  val dlg = remember { ConfirmationDialog() }
  dlg.DrawDialog()
  val reloader = remember { BasicReloader() }

  Column(
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.testTag(Destination.SETTINGS.name).padding(16.dp).fillMaxWidth(),
  ) {
    Text(text = "Settings")

    Spacer(modifier = Modifier.height(16.dp))

    EmbeddedSettingItem.entries.forEach { it.Draw(reloader.getKey()) }

    // Reset Button
    Button(
      onClick = {
        ALL_SETTINGS_ITEMS.forEach { it.reset() }
        reloader.reload()
      },
      modifier = Modifier.testTag("resetConfigButton"),
    ) {
      Text("Reset to Default Values")
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Erase All Data Button
    Button(
      onClick = {
        dlg.show {
          coroutineScope.launch {
            getDatabase().deleteAllNodes()
            getDatabase().deleteAllMoves()
            NodeManager.resetCacheFromDataBase()
          }
        }
      },
      modifier = Modifier.testTag("eraseAllDataButton"),
    ) {
      Text("Erase All Data")
    }
  }
}
