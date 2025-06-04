package proj.memorchess.axl.ui.popup

import android.widget.Toast
import proj.memorchess.axl.MAIN_ACTIVITY

private var currentToast: Toast = Toast.makeText(MAIN_ACTIVITY, "", Toast.LENGTH_SHORT)

actual fun getPopupRenderer() = IPopupRenderer { message, _ ->
  currentToast.cancel()
  currentToast = Toast.makeText(MAIN_ACTIVITY, message, Toast.LENGTH_SHORT)
  currentToast.show()
}
