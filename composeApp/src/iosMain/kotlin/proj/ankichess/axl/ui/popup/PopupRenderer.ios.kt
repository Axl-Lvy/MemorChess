package proj.ankichess.axl.ui.popup

object IosPopupRenderer : IPopupRenderer {
  override fun renderPopup(message: String, type: PopupType) {
    // TODO
  }
}

actual fun getPopupRenderer(): IPopupRenderer = IosPopupRenderer
