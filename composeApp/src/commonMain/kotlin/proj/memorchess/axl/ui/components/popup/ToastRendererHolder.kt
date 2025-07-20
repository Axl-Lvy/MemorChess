package proj.memorchess.axl.ui.components.popup

object ToastRendererHolder {
  private var toastRenderer: ToastRenderer? = null

  fun init(renderer: ToastRenderer) {
    toastRenderer = renderer
  }

  fun get(): ToastRenderer {
    val finalToastRenderer = toastRenderer
    if (finalToastRenderer != null) {
      return finalToastRenderer
    }
    val newToastRenderer = getToastRenderer()
    toastRenderer = newToastRenderer
    return newToastRenderer
  }
}
