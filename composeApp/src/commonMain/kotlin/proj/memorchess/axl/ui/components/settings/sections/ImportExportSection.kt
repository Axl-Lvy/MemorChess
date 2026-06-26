package proj.memorchess.axl.ui.components.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import memorchess.composeapp.generated.resources.lichess_study_http_error
import memorchess.composeapp.generated.resources.lichess_study_import_failed
import memorchess.composeapp.generated.resources.lichess_study_import_success
import memorchess.composeapp.generated.resources.lichess_study_invalid_url
import memorchess.composeapp.generated.resources.lichess_study_malformed_pgn
import memorchess.composeapp.generated.resources.lichess_study_network_error
import memorchess.composeapp.generated.resources.lichess_study_not_found
import memorchess.composeapp.generated.resources.settings_export
import memorchess.composeapp.generated.resources.settings_import
import memorchess.composeapp.generated.resources.settings_import_confirm
import memorchess.composeapp.generated.resources.settings_import_invalid
import memorchess.composeapp.generated.resources.settings_lichess_study_field
import memorchess.composeapp.generated.resources.settings_lichess_study_import
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.study.LichessStudyImportResult
import proj.memorchess.axl.core.data.study.LichessStudyImporter
import proj.memorchess.axl.core.data.study.LichessStudyResult
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.GraphSerializer
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.util.exportToFile

/** Rows fetched per bounded page when streaming the export to disk. */
private const val EXPORT_PAGE_SIZE = 256

/**
 * Import / Export settings section content.
 *
 * Two large Kinetic buttons (Export, Import) stacked in a Row. The action is **local** — opening
 * trees are serialized to / loaded from `.memorchess` files on the device's filesystem. Below them,
 * a text field imports a public Lichess study by URL or id; this calls the Lichess API but needs no
 * account.
 *
 * Earlier revisions included a 4-cell placeholder meta grid (positions / lines / last backup /
 * size) but every cell rendered as `"—"` because the persistence layer doesn't expose those metrics
 * without touching `core/`. The grid was visual noise and was removed; surface it again once the
 * real metrics are available.
 */
@Composable
fun ImportExportSection(
  database: DatabaseQueryManager = koinInject(),
  studyImporter: LichessStudyImporter = koinInject(),
) {
  val dlg = remember { ConfirmationDialog() }
  dlg.DrawDialog()

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    FileButtonsRow(database, dlg)
    LichessStudyImportField(studyImporter)
  }
}

/** The Export and Import file buttons of the Import / Export section. */
@Composable
private fun FileButtonsRow(database: DatabaseQueryManager, dlg: ConfirmationDialog) {
  val coroutineScope = rememberCoroutineScope()

  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    KineticButton(
      onClick = {
        coroutineScope.launch {
          // Stream the export through bounded pages so the whole store is never held in memory at
          // once; the serialized bytes are identical to an eager serialize of every node.
          val content =
            GraphSerializer.serializeStreaming(EXPORT_PAGE_SIZE) { cursor, limit ->
              database.getNodesPage(cursor, limit)
            }
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
                // The inserted rows are authoritative on disk; the bounded cache resolves them on
                // demand, so no eager reload is needed.
                database.insertNodes(*nodes.toTypedArray())
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

/**
 * Text field and button that import a public Lichess study by URL or id.
 *
 * The button is disabled while the input is blank or an import is in flight, so a double tap cannot
 * start two imports. The outcome of the last import, success summary or typed error, is rendered
 * below the button.
 */
@Composable
private fun LichessStudyImportField(studyImporter: LichessStudyImporter) {
  val coroutineScope = rememberCoroutineScope()
  var input by remember { mutableStateOf("") }
  var importing by remember { mutableStateOf(false) }
  var resultMessage by remember { mutableStateOf<String?>(null) }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    OutlinedTextField(
      value = input,
      onValueChange = { input = it },
      label = { Text(text = stringResource(Res.string.settings_lichess_study_field)) },
      singleLine = true,
      enabled = !importing,
      modifier = Modifier.fillMaxWidth().testTag("lichessStudyInput"),
    )
    KineticButton(
      onClick = {
        if (importing || input.isBlank()) {
          return@KineticButton
        }
        importing = true
        resultMessage = null
        coroutineScope.launch {
          try {
            resultMessage = describeStudyImport(studyImporter.import(input))
          } finally {
            importing = false
          }
        }
      },
      enabled = !importing && input.isNotBlank(),
      modifier = Modifier.fillMaxWidth().testTag("lichessStudyImportButton"),
      style = KineticButtonStyle.Default,
    ) {
      Text(text = stringResource(Res.string.settings_lichess_study_import))
    }
    val message = resultMessage
    if (message != null) {
      Text(text = message, modifier = Modifier.testTag("lichessStudyImportResult"))
    }
  }
}

/** Maps a study import outcome to the localized message shown under the import button. */
private suspend fun describeStudyImport(result: LichessStudyImportResult): String =
  when (result) {
    is LichessStudyImportResult.Success ->
      getString(
        Res.string.lichess_study_import_success,
        result.summary.movesAdded,
        result.summary.movesAlreadyPresent,
      )
    is LichessStudyImportResult.ImportFailed ->
      getString(Res.string.lichess_study_import_failed, result.message)
    is LichessStudyImportResult.FetchFailed ->
      when (val error = result.error) {
        is LichessStudyResult.InvalidUrl -> getString(Res.string.lichess_study_invalid_url)
        is LichessStudyResult.NotFound -> getString(Res.string.lichess_study_not_found)
        is LichessStudyResult.HttpError ->
          getString(Res.string.lichess_study_http_error, error.status)
        is LichessStudyResult.NetworkError -> getString(Res.string.lichess_study_network_error)
        is LichessStudyResult.MalformedPgn ->
          getString(Res.string.lichess_study_malformed_pgn, error.message)
      }
  }
