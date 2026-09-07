package proj.memorchess.axl.ui.components.repertoire

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.dialog_cancel
import memorchess.composeapp.generated.resources.library_create
import memorchess.composeapp.generated.resources.library_create_dialog_title
import memorchess.composeapp.generated.resources.library_create_name_label
import memorchess.composeapp.generated.resources.library_create_pgn_label
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.ui.components.popup.KineticDialog

/**
 * Create-repertoire dialog: a name, a side picker (white/black/mixed — `null` backs mixed), and an
 * optional PGN paste to seed it with moves immediately. Submit is disabled only while [name] is
 * blank; server-side style failures (a taken id, a malformed paste) are reported back through
 * [errorMessage] once the caller's submit attempt returns.
 *
 * Fields reset to blank every time [visible] turns true, so a dialog reopened after a cancel or a
 * completed create starts fresh rather than keeping a stale edit.
 */
@Composable
fun CreateRepertoireDialog(
  visible: Boolean,
  working: Boolean,
  errorMessage: String?,
  onSubmit: (name: String, color: RepertoireColor?, pgnText: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var color by remember { mutableStateOf<RepertoireColor?>(null) }
  var pgnText by remember { mutableStateOf("") }
  LaunchedEffect(visible) {
    if (visible) {
      name = ""
      color = null
      pgnText = ""
    }
  }

  KineticDialog(
    visible = visible,
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("createRepertoireDialog"),
    buttons = {
      TextButton(
        modifier = Modifier.testTag("createRepertoireDialogCancelButton"),
        onClick = onDismiss,
      ) {
        Text(stringResource(Res.string.dialog_cancel))
      }
      TextButton(
        modifier = Modifier.testTag("createRepertoireDialogSubmitButton"),
        enabled = !working && name.isNotBlank(),
        onClick = { onSubmit(name, color, pgnText) },
      ) {
        Text(stringResource(Res.string.library_create))
      }
    },
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stringResource(Res.string.library_create_dialog_title))
      TextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(Res.string.library_create_name_label)) },
        isError = name.isBlank(),
        modifier = Modifier.testTag("createRepertoireDialogNameField"),
      )
      RepertoireColorPicker(
        selected = color,
        onSelect = { color = it },
        modifier = Modifier.testTag("createRepertoireDialogColorControl"),
      )
      TextField(
        value = pgnText,
        onValueChange = { pgnText = it },
        label = { Text(stringResource(Res.string.library_create_pgn_label)) },
        modifier = Modifier.testTag("createRepertoireDialogPgnField"),
      )
      if (errorMessage != null) {
        Text(errorMessage)
      }
    }
  }
}
