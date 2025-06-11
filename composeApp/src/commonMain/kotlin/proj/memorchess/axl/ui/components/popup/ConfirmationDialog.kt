package proj.memorchess.axl.ui.components.popup

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A confirmation dialog that can be shown with a confirmation action.
 */
class ConfirmationDialog {
  private var show by mutableStateOf(true)
  private var onConfirm = {}

  /**
   * Show the confirmation dialog with the given confirmation action.
   *
   * @param confirm The action to perform when the user confirms.
   */
  fun show(confirm: () -> Unit) {
    onConfirm = confirm
    show = true
  }

  @Composable
  fun DrawDialog() {
    if (show) {
      AlertDialog(
        onDismissRequest = {},
        confirmButton = {
          TextButton(
            onClick = {
              onConfirm()
              onConfirm = {}
              show = false
            }
          ) {
            Text("OK")
          }
        },
        dismissButton = {
          TextButton(
            onClick = {
              onConfirm = {}
              show = false
            }
          ) {
            Text("Cancel")
          }
        },
        title = { Text("Confirm?") },
      )
    }
  }
}
