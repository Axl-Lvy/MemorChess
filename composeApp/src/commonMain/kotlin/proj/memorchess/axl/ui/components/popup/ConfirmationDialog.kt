package proj.memorchess.axl.ui.components.popup

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** A confirmation dialog that can be shown with a confirmation action. */
class ConfirmationDialog(
  private val okText: String = "OK",
  private val cancelText: String = "Cancel",
) {
  private var show by mutableStateOf(false)
  private var content by mutableStateOf<@Composable () -> Unit>({})
  private var onConfirm = {}

  /**
   * Show the confirmation dialog with the given confirmation action.
   *
   * @param text The text to show in the dialog.
   * @param confirm The action to perform when the user confirms.
   */
  fun show(text: String, confirm: () -> Unit) {
    show(confirm, content = { Text(text) })
  }

  /**
   * Show the confirmation dialog with the given confirmation action and content.
   *
   * @param confirm The action to perform when the user confirms.
   * @param content The composable content to show in the dialog.
   */
  fun show(confirm: () -> Unit, content: @Composable () -> Unit) {
    onConfirm = confirm
    this.content = content
    show = true
  }

  @Composable
  fun DrawDialog() {
    if (show) {
      AlertDialog(
        modifier = Modifier.testTag("confirmDialog"),
        onDismissRequest = {},
        confirmButton = {
          TextButton(
            onClick = {
              onConfirm()
              onConfirm = {}
              show = false
            }
          ) {
            Text(okText)
          }
        },
        dismissButton = {
          TextButton(
            onClick = {
              onConfirm = {}
              show = false
            }
          ) {
            Text(cancelText)
          }
        },
        title = { content() },
      )
    }
  }
}
