package proj.memorchess.axl.ui.popup

/** Popup renderer. */
fun interface IPopupRenderer {
  fun renderPopup(message: String, type: PopupType)
}

/** Retrieves the platform specific [IPopupRenderer]. */
expect fun getPopupRenderer(): IPopupRenderer

fun popup(message: String, type: PopupType) {
  PopupRendererHolder.get().renderPopup(message, type)
}

fun info(message: String) {
  popup(message, PopupType.INFO)
}

val noOpPopupRenderer = IPopupRenderer {_, _ -> }
