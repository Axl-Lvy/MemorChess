package proj.ankichess.axl.ui.popup

object WasmPopupRenderer : IPopupRenderer {
  override fun renderPopup(message: String, type: PopupType) {
    // TODO
  }
}

actual fun getPopupRenderer(): IPopupRenderer = WasmPopupRenderer
