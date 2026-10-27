package proj.memorchess.axl.ui.components.popup

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.dialog_cancel
import memorchess.composeapp.generated.resources.dialog_ok
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A dialog that runs an action once the user confirms. */
class ConfirmationDialog(
  private val okText: StringResource = Res.string.dialog_ok,
  private val cancelText: StringResource = Res.string.dialog_cancel,
) {
  private var show by mutableStateOf(false)
  private var content by mutableStateOf<@Composable () -> Unit>({})
  private var onConfirm = {}

  /** Shows [text] and runs [confirm] if the user confirms. */
  fun show(text: String, confirm: () -> Unit) {
    show(confirm, content = { Text(text) })
  }

  /** Shows [content] and runs [confirm] if the user confirms. */
  fun show(confirm: () -> Unit, content: @Composable () -> Unit) {
    onConfirm = confirm
    this.content = content
    show = true
  }

  @Composable
  fun DrawDialog() {
    KineticDialog(
      visible = show,
      onDismissRequest = {
        onConfirm = {}
        show = false
      },
      modifier = Modifier.testTag("confirmDialog"),
      buttons = {
        TextButton(
          modifier = Modifier.testTag("confirmDialogCancelButton"),
          onClick = {
            onConfirm = {}
            show = false
          },
        ) {
          Text(stringResource(cancelText))
        }
        TextButton(
          modifier = Modifier.testTag("confirmDialogOkButton"),
          onClick = {
            onConfirm()
            onConfirm = {}
            show = false
          },
        ) {
          Text(stringResource(okText))
        }
      },
    ) {
      content()
    }
  }
}
