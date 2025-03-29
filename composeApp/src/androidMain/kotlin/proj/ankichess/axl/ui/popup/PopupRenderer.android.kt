package proj.ankichess.axl.ui.popup

import android.widget.Toast
import proj.ankichess.axl.MAIN_ACTIVITY

object AndroidPopupRenderer : PopupRenderer {
  override fun renderPopup(message: String, type: PopupType) {
    Toast.makeText(MAIN_ACTIVITY, message, Toast.LENGTH_SHORT).show()
  }
}

actual fun getPopupRenderer(): PopupRenderer = AndroidPopupRenderer
