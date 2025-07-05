package proj.memorchess.axl.ui.components.popup

import android.widget.Toast
import proj.memorchess.axl.MAIN_ACTIVITY

private var currentToast: Toast? = null

actual fun getToastRenderer() = IToastRenderer { message, _ ->
  MAIN_ACTIVITY.runOnUiThread {
    currentToast?.cancel()
    currentToast = Toast.makeText(MAIN_ACTIVITY, message, Toast.LENGTH_SHORT)
    currentToast?.show()
  }
}
