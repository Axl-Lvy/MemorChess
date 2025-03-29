package proj.ankichess.axl.ui.popup

object WasmPopupRenderer : PopupRenderer {
  override fun renderPopup(message: String, type: PopupType) {
    // TODO
  }
}

actual fun getPopupRenderer(): PopupRenderer = WasmPopupRenderer
