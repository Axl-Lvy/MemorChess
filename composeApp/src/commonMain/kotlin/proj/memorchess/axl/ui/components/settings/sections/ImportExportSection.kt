package proj.memorchess.axl.ui.components.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import memorchess.composeapp.generated.resources.settings_lichess_new_repertoire
import memorchess.composeapp.generated.resources.settings_lichess_new_repertoire_name
import memorchess.composeapp.generated.resources.settings_lichess_no_repertoire
import memorchess.composeapp.generated.resources.settings_lichess_study_field
import memorchess.composeapp.generated.resources.settings_lichess_study_import
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import proj.memorchess.axl.core.data.DataRepertoire
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.study.LichessStudyImportResult
import proj.memorchess.axl.core.data.study.LichessStudyImporter
import proj.memorchess.axl.core.data.study.LichessStudyResult
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.GraphSerializer
import proj.memorchess.axl.core.graph.RepertoireTagStore
import proj.memorchess.axl.core.graph.TreeStore
import proj.memorchess.axl.ui.components.buttons.KineticButton
import proj.memorchess.axl.ui.components.buttons.KineticButtonStyle
import proj.memorchess.axl.ui.components.controls.KineticSegmentedControl
import proj.memorchess.axl.ui.components.popup.ConfirmationDialog
import proj.memorchess.axl.ui.util.exportToFile

private const val EXPORT_PAGE_SIZE = 256

/** Import / Export settings section: local `.memorchess` file backup and Lichess study import. */
@Composable
fun ImportExportSection(
  database: DatabaseQueryManager = koinInject(),
  treeStore: TreeStore = koinInject(),
  studyImporter: LichessStudyImporter = koinInject(),
) {
  val dlg = remember { ConfirmationDialog() }
  dlg.DrawDialog()

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    FileButtonsRow(database, treeStore, dlg)
    LichessStudyImportField(studyImporter)
  }
}

@Composable
private fun FileButtonsRow(
  database: DatabaseQueryManager,
  treeStore: TreeStore,
  dlg: ConfirmationDialog,
) {
  val coroutineScope = rememberCoroutineScope()

  // IntrinsicSize.Min lets both buttons match the taller one when a label wraps.
  Row(
    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    KineticButton(
      onClick = {
        coroutineScope.launch {
          // Bounded pages keep the whole store out of memory.
          val content =
            GraphSerializer.serializeStreaming(EXPORT_PAGE_SIZE) { cursor, limit ->
              database.getNodesPage(cursor, limit)
            }
          val baseName = "openings-${DateUtil.today()}"
          exportToFile(content, baseName, "memorchess")
        }
      },
      modifier = Modifier.weight(1f).fillMaxHeight().testTag("exportButton"),
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
                // Through TreeStore so the import gets this device's stamps and is queued for sync.
                treeStore.importNodes(nodes)
              }
            }
          } catch (e: IllegalArgumentException) {
            Logger.e("Import failed", e)
            dlg.show(getString(Res.string.settings_import_invalid, e.message ?: "")) {}
          }
        }
      },
      modifier = Modifier.weight(1f).fillMaxHeight().testTag("importButton"),
      style = KineticButtonStyle.Default,
      large = true,
    ) {
      Text(text = stringResource(Res.string.settings_import))
    }
  }
}

/**
 * Imports a public Lichess study by URL or id, tagging its moves with the repertoire chosen in
 * [RepertoirePicker].
 */
@Composable
internal fun LichessStudyImportField(
  studyImporter: LichessStudyImporter,
  tagStore: RepertoireTagStore = koinInject(),
) {
  val coroutineScope = rememberCoroutineScope()
  var input by remember { mutableStateOf("") }
  var importing by remember { mutableStateOf(false) }
  var resultMessage by remember { mutableStateOf<String?>(null) }
  var repertoires by remember { mutableStateOf<List<DataRepertoire>>(emptyList()) }
  // null means no repertoire: the import stays untagged.
  var selectedRepertoireId by remember { mutableStateOf<String?>(null) }
  var newRepertoireName by remember { mutableStateOf("") }

  LaunchedEffect(Unit) { repertoires = tagStore.repertoires() }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    RepertoirePicker(
      repertoires = repertoires,
      selectedId = selectedRepertoireId,
      onSelect = { selectedRepertoireId = it },
      newName = newRepertoireName,
      onNewNameChange = { newRepertoireName = it },
    )
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
        if (importing || input.isBlank()) return@KineticButton
        importing = true
        resultMessage = null
        coroutineScope.launch {
          try {
            val repertoireId =
              when (selectedRepertoireId) {
                NEW_REPERTOIRE_SENTINEL -> {
                  val id = newRepertoireName.trim().lowercase().replace(' ', '-')
                  tagStore.register(id, newRepertoireName.trim(), color = null)
                  id
                }
                else -> selectedRepertoireId
              }
            resultMessage = describeStudyImport(studyImporter.import(input, repertoireId))
          } finally {
            importing = false
          }
        }
      },
      enabled =
        !importing &&
          input.isNotBlank() &&
          (selectedRepertoireId != NEW_REPERTOIRE_SENTINEL || newRepertoireName.isNotBlank()),
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

/** Sentinel `selectedRepertoireId` value meaning "create a new repertoire named [newName]". */
private const val NEW_REPERTOIRE_SENTINEL = "__new__"

/**
 * Picker over no repertoire (`null`), the existing repertoires and a new one, whose [newName] field
 * shows only while it is selected.
 */
@Composable
private fun RepertoirePicker(
  repertoires: List<DataRepertoire>,
  selectedId: String?,
  onSelect: (String?) -> Unit,
  newName: String,
  onNewNameChange: (String) -> Unit,
) {
  val noRepertoireLabel = stringResource(Res.string.settings_lichess_no_repertoire)
  val newRepertoireLabel = stringResource(Res.string.settings_lichess_new_repertoire)
  val options = listOf(null) + repertoires.map { it.id } + NEW_REPERTOIRE_SENTINEL
  val labelById =
    buildMap<String?, String> {
      put(null, noRepertoireLabel)
      repertoires.forEach { put(it.id, it.name) }
      put(NEW_REPERTOIRE_SENTINEL, newRepertoireLabel)
    }
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    KineticSegmentedControl(
      options = options,
      selected = selectedId,
      onSelect = onSelect,
      label = { labelById.getValue(it) },
      modifier = Modifier.fillMaxWidth().testTag("lichessRepertoirePicker"),
    )
    if (selectedId == NEW_REPERTOIRE_SENTINEL) {
      OutlinedTextField(
        value = newName,
        onValueChange = onNewNameChange,
        label = { Text(text = stringResource(Res.string.settings_lichess_new_repertoire_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("lichessNewRepertoireName"),
      )
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
