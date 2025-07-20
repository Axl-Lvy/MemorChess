package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import proj.memorchess.axl.core.config.ALL_SETTINGS_ITEMS
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.ui.components.buttons.SignInButton
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.components.settings.EmbeddedSettingItem
import proj.memorchess.axl.ui.components.settings.SyncStatusSection
import proj.memorchess.axl.ui.pages.navigation.Destination
import proj.memorchess.axl.ui.util.BasicReloader

@Composable
fun Settings(database: DatabaseQueryManager = koinInject()) {
  val coroutineScope = rememberCoroutineScope()
  val dlg = remember { ConfirmationDialog() }
  dlg.DrawDialog()
  val reloader = remember { BasicReloader() }

  Box(
    modifier =
      Modifier.testTag(Destination.SETTINGS.name)
        .padding(16.dp)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
      Text(text = "Settings")

      Spacer(modifier = Modifier.height(16.dp))

      EmbeddedSettingItem.entries.forEach {
        it.Draw(reloader.getKey())
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Reset Button
      Button(
        onClick = {
          dlg.show("Are you sure you want to reset all settings?") {
            ALL_SETTINGS_ITEMS.forEach { it.reset() }
            reloader.reload()
          }
        },
        modifier = Modifier.testTag("resetConfigButton").fillMaxWidth(),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
      ) {
        Icon(
          Icons.Filled.Refresh,
          contentDescription = "Reset",
          modifier = Modifier.padding(end = 8.dp),
        )
        Text("Reset to Default Values")
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Erase All Data Button
      Button(
        onClick = {
          dlg.show("Are you sure you want to erase all data?") {
            coroutineScope.launch {
              database.deleteAll(null)
              NodeManager.resetCacheFromDataBase()
            }
          }
        },
        modifier = Modifier.testTag("eraseAllDataButton").fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        shape = ButtonDefaults.shape,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
      ) {
        Icon(
          Icons.Filled.Delete,
          contentDescription = "Erase",
          modifier = Modifier.padding(end = 8.dp),
        )
        Text("Erase All Data")
      }

      // --- Sign In Button ---
      SignInButton()
      SyncStatusSection()
    }
  }
}
