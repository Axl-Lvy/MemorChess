package proj.ankichess.axl.ui.popup

/** Popup renderer. */
interface PopupRenderer {
  fun renderPopup(message: String, type: PopupType)
}

/** Retrieves the platform specific [PopupRenderer]. */
expect fun getPopupRenderer(): PopupRenderer

fun popup(message: String, type: PopupType) {
  getPopupRenderer().renderPopup(message, type)
}

fun info(message: String) {
  popup(message, PopupType.INFO)
}
