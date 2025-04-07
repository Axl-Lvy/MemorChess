package proj.ankichess.axl.ui.popup

object AndroidPopupRenderer : PopupRenderer {
  override fun renderPopup(message: String, type: PopupType) {}
}

actual fun getPopupRenderer(): PopupRenderer = AndroidPopupRenderer
