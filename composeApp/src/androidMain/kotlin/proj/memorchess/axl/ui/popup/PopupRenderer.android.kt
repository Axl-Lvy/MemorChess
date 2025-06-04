package proj.memorchess.axl.ui.popup

import android.widget.Toast
import proj.memorchess.axl.MAIN_ACTIVITY

object AndroidPopupRenderer : IPopupRenderer {
  private var currentToast: Toast = Toast.makeText(MAIN_ACTIVITY, "", Toast.LENGTH_SHORT)

  override fun renderPopup(message: String, type: PopupType) {
    currentToast.cancel()
    currentToast = Toast.makeText(MAIN_ACTIVITY, message, Toast.LENGTH_SHORT)
    currentToast.show()
  }
}

actual fun getPopupRenderer(): IPopupRenderer = AndroidPopupRenderer
