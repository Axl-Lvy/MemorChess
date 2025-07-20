package proj.memorchess.axl.ui.components.popup

/** Popup renderer. */
fun interface ToastRenderer {
  fun toast(message: String, type: ToastType)
}

/** Retrieves the platform specific [ToastRenderer]. */
expect fun getToastRenderer(): ToastRenderer

fun popup(message: String, type: ToastType) {
  ToastRendererHolder.get().toast(message, type)
}

fun info(message: String) {
  popup(message, ToastType.INFO)
}

val noOpToastRenderer = ToastRenderer { _, _ -> }
