package proj.memorchess.axl.ui.components.popup

/** Popup renderer. */
fun interface ToastRenderer {
  fun toast(message: String, type: ToastType)

  fun info(message: String) {
    toast(message, ToastType.INFO)
  }
}

/** Retrieves the platform specific [ToastRenderer]. */
expect fun getPlatformSpecificToastRenderer(): ToastRenderer

val NO_OP_RENDERER = ToastRenderer { _, _ -> }
