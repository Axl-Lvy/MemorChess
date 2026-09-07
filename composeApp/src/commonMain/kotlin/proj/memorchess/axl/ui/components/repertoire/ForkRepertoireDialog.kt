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
import memorchess.composeapp.generated.resources.library_color_black
import memorchess.composeapp.generated.resources.library_color_white
import memorchess.composeapp.generated.resources.library_create_color_mixed
import memorchess.composeapp.generated.resources.library_fork
import memorchess.composeapp.generated.resources.library_fork_default_name
import memorchess.composeapp.generated.resources.library_fork_dialog_title
import memorchess.composeapp.generated.resources.library_fork_name_label
import org.jetbrains.compose.resources.stringResource
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.ui.components.controls.KineticSegmentedControl
import proj.memorchess.axl.ui.components.popup.KineticDialog

/**
 * Fork-repertoire dialog: duplicates [sourceName] under a new name, prefilled from
 * [Res.string.library_fork_default_name] so a plain submit already produces a sensible id. The side
 * picker defaults to [sourceColor] but stays editable, since the copy can be re-tagged for a
 * different side than the source repertoire. The new repertoire copies every one of the source's
 * tagged edges regardless of the side chosen here (see
 * [proj.memorchess.axl.core.graph.TreeStore.forkRepertoire]).
 *
 * Fields reset to [sourceName]/[sourceColor]'s defaults every time [visible] turns true.
 */
@Composable
fun ForkRepertoireDialog(
  visible: Boolean,
  sourceName: String,
  sourceColor: RepertoireColor?,
  working: Boolean,
  errorMessage: String?,
  onSubmit: (newName: String, color: RepertoireColor?) -> Unit,
  onDismiss: () -> Unit,
) {
  val suggestedName = stringResource(Res.string.library_fork_default_name, sourceName)
  var newName by remember { mutableStateOf(suggestedName) }
  var color by remember { mutableStateOf(sourceColor) }
  LaunchedEffect(visible) {
    if (visible) {
      newName = suggestedName
      color = sourceColor
    }
  }

  KineticDialog(
    visible = visible,
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("forkRepertoireDialog"),
    buttons = {
      TextButton(
        modifier = Modifier.testTag("forkRepertoireDialogCancelButton"),
        onClick = onDismiss,
      ) {
        Text(stringResource(Res.string.dialog_cancel))
      }
      TextButton(
        modifier = Modifier.testTag("forkRepertoireDialogSubmitButton"),
        enabled = !working && newName.isNotBlank(),
        onClick = { onSubmit(newName, color) },
      ) {
        Text(stringResource(Res.string.library_fork))
      }
    },
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stringResource(Res.string.library_fork_dialog_title))
      TextField(
        value = newName,
        onValueChange = { newName = it },
        label = { Text(stringResource(Res.string.library_fork_name_label)) },
        isError = newName.isBlank(),
        modifier = Modifier.testTag("forkRepertoireDialogNameField"),
      )
      val whiteLabel = stringResource(Res.string.library_color_white)
      val blackLabel = stringResource(Res.string.library_color_black)
      val mixedLabel = stringResource(Res.string.library_create_color_mixed)
      KineticSegmentedControl(
        options = listOf(RepertoireColor.WHITE, RepertoireColor.BLACK, null),
        selected = color,
        onSelect = { color = it },
        label = {
          when (it) {
            RepertoireColor.WHITE -> whiteLabel
            RepertoireColor.BLACK -> blackLabel
            null -> mixedLabel
          }
        },
        modifier = Modifier.testTag("forkRepertoireDialogColorControl"),
      )
      if (errorMessage != null) {
        Text(errorMessage)
      }
    }
  }
}
