package proj.memorchess.axl.ui.components.popup

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import memorchess.composeapp.generated.resources.Res
import memorchess.composeapp.generated.resources.dialog_ok
import org.jetbrains.compose.resources.stringResource

/** A simple alert dialog that can be shown with a message. */
class SimpleAlert {
  private var show by mutableStateOf(false)
  private var text: String = ""

  /**
   * Show the alert dialog with the given text.
   *
   * @param text The message to display in the dialog.
   */
  fun show(text: String) {
    this.text = text
    show = true
  }

  @Composable
  fun DrawDialog() {
    if (show) {
      AlertDialog(
        onDismissRequest = {},
        confirmButton = {
          TextButton(onClick = { show = false }) { Text(stringResource(Res.string.dialog_ok)) }
        },
        title = { Text(text) },
      )
    }
  }
}
