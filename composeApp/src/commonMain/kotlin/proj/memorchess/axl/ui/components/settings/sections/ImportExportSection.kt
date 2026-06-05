package proj.memorchess.axl.ui.components.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readString
import kotlinx.coroutines.launch
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.settings_export
import memorchess.composeapp.generated.resources.settings_import
import memorchess.composeapp.generated.resources.settings_import_confirm
import memorchess.composeapp.generated.resources.settings_import_invalid
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.GraphSerializer
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.util.exportToFile

/**
 * Import / Export settings section content.
 *
 * Two large Kinetic buttons (Export, Import) stacked in a Row. The action is **local** — opening
 * trees are serialized to / loaded from `.memorchess` files on the device's filesystem. There is no
 * Lichess account involvement here.
 *
 * Earlier revisions included a 4-cell placeholder meta grid (positions / lines / last backup /
 * size) but every cell rendered as `"—"` because the persistence layer doesn't expose those metrics
 * without touching `core/`. The grid was visual noise and was removed; surface it again once the
 * real metrics are available.
 */
@Composable
fun ImportExportSection(
  database: DatabaseQueryManager = koinInject(),
  treeStore: TreeStore = koinInject(),
) {
  val coroutineScope = rememberCoroutineScope()
  val dlg = remember { ConfirmationDialog() }
  dlg.DrawDialog()

  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    KineticButton(
      onClick = {
        coroutineScope.launch {
          val nodes = database.getAllNodes()
          val content = GraphSerializer.serialize(nodes)
          val baseName = "openings-${DateUtil.today()}"
          exportToFile(content, baseName, "memorchess")
        }
      },
      modifier = Modifier.weight(1f).testTag("exportButton"),
      style = KineticButtonStyle.Default,
      large = true,
    ) {
      Text(text = stringResource(Res.string.settings_export))
    }
    KineticButton(
      onClick = {
        coroutineScope.launch {
          try {
            val file =
              FileKit.openFilePicker(type = FileKitType.File("memorchess")) ?: return@launch
            val content = file.readString()
            dlg.show(getString(Res.string.settings_import_confirm, file.name)) {
              coroutineScope.launch {
                val nodes = GraphSerializer.deserialize(content)
                database.insertNodes(*nodes.toTypedArray())
                treeStore.load()
              }
            }
          } catch (e: IllegalArgumentException) {
            Logger.e("Import failed", e)
            dlg.show(getString(Res.string.settings_import_invalid, e.message ?: "")) {}
          }
        }
      },
      modifier = Modifier.weight(1f).testTag("importButton"),
      style = KineticButtonStyle.Default,
      large = true,
    ) {
      Text(text = stringResource(Res.string.settings_import))
    }
  }
}
