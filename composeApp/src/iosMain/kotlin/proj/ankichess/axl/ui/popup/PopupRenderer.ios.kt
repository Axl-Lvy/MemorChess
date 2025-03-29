package proj.ankichess.axl.ui.popup

object IosPopupRenderer : PopupRenderer {
  override fun renderPopup(message: String, type: PopupType) {
    // TODO
  }
}

actual fun getPopupRenderer(): PopupRenderer = IosPopupRenderer
