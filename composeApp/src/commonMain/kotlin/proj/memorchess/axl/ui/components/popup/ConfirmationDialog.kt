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

/**
 * A confirmation dialog that can be shown with a confirmation action.
 *
 * @param okText String resource for the confirm button label (defaults to [Res.string.dialog_ok]).
 * @param cancelText String resource for the dismiss button label (defaults to
 *   [Res.string.dialog_cancel]).
 */
class ConfirmationDialog(
  private val okText: StringResource = Res.string.dialog_ok,
  private val cancelText: StringResource = Res.string.dialog_cancel,
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
    KineticDialog(
      visible = show,
      // Tapping outside the dialog or pressing system back dismisses it as a cancel: drop the
      // pending confirm action and hide, exactly like the Cancel button.
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
