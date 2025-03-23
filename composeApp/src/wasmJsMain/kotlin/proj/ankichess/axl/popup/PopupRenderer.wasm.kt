package proj.ankichess.axl.popup

object WasmPopupRenderer : PopupRenderer {
  override fun renderPopup(message: String, type: PopupType) {}
}

actual fun getPopupRenderer(): PopupRenderer = WasmPopupRenderer
