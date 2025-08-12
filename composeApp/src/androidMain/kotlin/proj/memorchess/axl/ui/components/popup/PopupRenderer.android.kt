package proj.memorchess.axl.ui.components.popup

import android.widget.Toast
import proj.memorchess.axl.MAIN_ACTIVITY

private var currentToast: Toast? = null

actual fun getPlatformSpecificToastRenderer() = ToastRenderer { message, _ ->
  MAIN_ACTIVITY.runOnUiThread {
    currentToast?.cancel()
    val newToast = Toast.makeText(MAIN_ACTIVITY, message, Toast.LENGTH_LONG)
    newToast.show()
    currentToast = newToast
  }
}
