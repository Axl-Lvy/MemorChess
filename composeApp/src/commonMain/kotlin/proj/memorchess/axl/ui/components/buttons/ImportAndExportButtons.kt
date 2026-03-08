package proj.memorchess.axl.ui.components.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.GraphSerializer
import proj.memorchess.axl.core.graph.nodes.NodeManager
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.util.exportToFile

/**
 * Row with Export and Import buttons for opening-tree data.
 *
 * **Export** serializes all nodes via [GraphSerializer] and saves them to a `.memorchess` file
 * using a platform file-saver dialog (or browser download on wasmJs).
 *
 * **Import** opens a file picker filtered to `.memorchess` files, asks for confirmation, then
 * deserializes and merges the nodes into the database (existing positions are overwritten).
 */
@Composable
fun ImportAndExportButtons(
  database: DatabaseQueryManager = koinInject(),
  nodeManager: NodeManager = koinInject(),
) {
  val coroutineScope = rememberCoroutineScope()
  val dlg = remember { ConfirmationDialog() }
  dlg.DrawDialog()
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
}
