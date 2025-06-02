package proj.ankichess.axl.ui.popup

object DesktopPopupRenderer : IPopupRenderer {
  override fun renderPopup(message: String, type: PopupType) {
    // No-op for desktop, as we don't have a popup mechanism in this context.
  }
}

actual fun getPopupRenderer(): IPopupRenderer = DesktopPopupRenderer
