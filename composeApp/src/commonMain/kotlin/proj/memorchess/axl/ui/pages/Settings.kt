package proj.memorchess.axl.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import co.touchlab.kermit.Logger
import compose.icons.FeatherIcons
import compose.icons.feathericons.Download
import compose.icons.feathericons.Upload
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readString
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import proj.memorchess.axl.core.config.ALL_SETTINGS_ITEMS
import proj.memorchess.axl.core.config.FeatureFlags
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.GraphSerializer
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.ui.components.buttons.SignInButton
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.components.settings.EmbeddedSettingItem
import proj.memorchess.axl.ui.components.settings.SyncStatusSection
import proj.memorchess.axl.ui.pages.navigation.Route
import proj.memorchess.axl.ui.util.BasicReloader
import proj.memorchess.axl.ui.util.exportToFile

@Composable
fun Settings(
  database: DatabaseQueryManager = koinInject(),
  nodeManager: NodeManager = koinInject(),
) {
  val coroutineScope = rememberCoroutineScope()
  val dlg = remember { ConfirmationDialog() }
  dlg.DrawDialog()
  val reloader = remember { BasicReloader() }

  Box(
    modifier =
      Modifier.testTag(Route.SettingsRoute.getLabel())
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
        Text("Reset to default values")
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Export / Import Buttons
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
          onClick = {
            coroutineScope.launch {
              val nodes = database.getAllNodes()
              val content = GraphSerializer.serialize(nodes)
              val baseName = "openings-${DateUtil.today()}"
              exportToFile(content, baseName, "memorchess")
            }
          },
          modifier = Modifier.testTag("exportButton").weight(1f),
          elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        ) {
          Icon(
            FeatherIcons.Upload,
            contentDescription = "Export",
            modifier = Modifier.padding(end = 8.dp),
          )
          Text("Export")
        }

        Button(
          onClick = {
            coroutineScope.launch {
              try {
                val file =
                  FileKit.openFilePicker(type = FileKitType.File("memorchess")) ?: return@launch
                val content = file.readString()
                dlg.show("Import openings from ${file.name}? This will merge with existing data.") {
                  coroutineScope.launch {
                    val nodes = GraphSerializer.deserialize(content)
                    database.insertNodes(*nodes.toTypedArray())
                    nodeManager.resetCacheFromSource()
                  }
                }
              } catch (e: IllegalArgumentException) {
                Logger.e("Import failed", e)
                dlg.show("Invalid file format: ${e.message}") {}
              }
            }
          },
          modifier = Modifier.testTag("importButton").weight(1f),
          elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        ) {
          Icon(
            FeatherIcons.Download,
            contentDescription = "Import",
            modifier = Modifier.padding(end = 8.dp),
          )
          Text("Import")
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Erase All Data Button
      Button(
        onClick = {
          dlg.show("Are you sure you want to erase all data?") {
            coroutineScope.launch {
              database.deleteAll(null)
              nodeManager.resetCacheFromSource()
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
        Text("Erase all data")
      }

      if (FeatureFlags.isAuthEnabled) {
        // --- Sign In Button ---
        SignInButton()
        SyncStatusSection()
      }
    }
  }
}
