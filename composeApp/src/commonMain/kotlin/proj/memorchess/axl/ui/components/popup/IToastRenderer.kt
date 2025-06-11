package proj.memorchess.axl.ui.components.popup

/** Popup renderer. */
fun interface IToastRenderer {
  fun toast(message: String, type: ToastType)
}

/** Retrieves the platform specific [IToastRenderer]. */
expect fun getToastRenderer(): IToastRenderer

fun popup(message: String, type: ToastType) {
  ToastRendererHolder.get().toast(message, type)
}

fun info(message: String) {
  popup(message, ToastType.INFO)
}

val noOpToastRenderer = IToastRenderer { _, _ -> }
