package proj.memorchess.axl.ui.components.settings.sections

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.GraphSerializer
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.theme.LocalKineticPalette
import proj.memorchess.axl.ui.theme.LocalKineticTypography
import proj.memorchess.axl.ui.util.exportToFile

/**
 * Import/Export settings section content.
 *
 * Two large Kinetic buttons (Export, Import) on top, followed by a 4-column meta grid showing
 * positions / lines / last backup / size. Live metrics are not currently exposed by the persistence
 * layer, so all four values are placeholders (`"—"`) until that data is surfaced.
 *
 * The OAuth-free import/export wiring is identical to
 * [proj.memorchess.axl.ui.components.buttons .ImportAndExportButtons]; the click handlers are
 * copied here so the surrounding visuals can be the Kinetic ones.
 */
@Composable
fun ImportExportSection(
  database: DatabaseQueryManager = koinInject(),
  treeStore: TreeStore = koinInject(),
) {
  val coroutineScope = rememberCoroutineScope()
  val dlg = remember { ConfirmationDialog() }
  dlg.DrawDialog()

  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
        Text(text = "EXPORT REPERTOIRE")
      }
      KineticButton(
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
                  treeStore.load()
                }
              }
            } catch (e: IllegalArgumentException) {
              Logger.e("Import failed", e)
              dlg.show("Invalid file format: ${e.message}") {}
            }
          }
        },
        modifier = Modifier.weight(1f).testTag("importButton"),
        style = KineticButtonStyle.Default,
        large = true,
      ) {
        Text(text = "IMPORT FILE")
      }
    }

    MetaGrid()
  }
}

/**
 * Four-cell metric strip used at the bottom of the import/export card.
 *
 * Values are placeholders (`"—"`) — the persistence layer doesn't currently expose position/line
 * counts or backup metadata without touching `core/`, which is out of scope here.
 */
@Composable
private fun MetaGrid() {
  val palette = LocalKineticPalette.current
  val items =
    listOf("positions" to "—", "saved lines" to "—", "last backup" to "—", "db size" to "—")
  Row(
    modifier = Modifier.fillMaxWidth().border(width = 1.dp, color = palette.line),
    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
  ) {
    items.forEachIndexed { index, (key, value) ->
      MetaCell(key = key, value = value, modifier = Modifier.weight(1f))
      if (index < items.lastIndex) {
        Box(
          modifier = Modifier.width(1.dp).height(56.dp).border(width = 1.dp, color = palette.line)
        )
      }
    }
  }
}

@Composable
private fun MetaCell(key: String, value: String, modifier: Modifier = Modifier) {
  val palette = LocalKineticPalette.current
  val typography = LocalKineticTypography.current
  Column(
    modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(text = key.uppercase(), style = typography.monoSm.copy(color = palette.ink4))
    Text(text = value, style = typography.display.copy(color = palette.ink))
  }
}
