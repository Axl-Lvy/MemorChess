package proj.ankichess.axl.ui.popup

import android.widget.Toast
import proj.ankichess.axl.MAIN_ACTIVITY

object AndroidPopupRenderer : PopupRenderer {
  private var currentToast: Toast = Toast.makeText(MAIN_ACTIVITY, "", Toast.LENGTH_SHORT)

  override fun renderPopup(message: String, type: PopupType) {
    currentToast.cancel()
    currentToast = Toast.makeText(MAIN_ACTIVITY, message, Toast.LENGTH_SHORT)
    currentToast.show()
  }
}

actual fun getPopupRenderer(): PopupRenderer = AndroidPopupRenderer
