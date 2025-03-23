package proj.ankichess.axl.popup

object IosPopupRenderer : PopupRenderer {
  override fun renderPopup(message: String, type: PopupType) {
    // TODO
  }
}

actual fun getPopupRenderer(): PopupRenderer = IosPopupRenderer
