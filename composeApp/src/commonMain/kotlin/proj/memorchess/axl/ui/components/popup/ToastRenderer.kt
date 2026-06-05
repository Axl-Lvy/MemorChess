package proj.memorchess.axl.ui.components.popup

import org.jetbrains.compose.resources.StringResource

/** Popup renderer. */
fun interface ToastRenderer {
  fun toast(message: StringResource, type: ToastType)

  fun info(message: StringResource) {
    toast(message, ToastType.INFO)
  }
}

/** Retrieves the platform specific [ToastRenderer]. */
expect fun getPlatformSpecificToastRenderer(): ToastRenderer

val NO_OP_RENDERER = ToastRenderer { _, _ -> }
