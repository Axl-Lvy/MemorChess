package proj.memorchess.axl.ui.components.popup

object ToastRendererHolder {
  private var toastRenderer: IToastRenderer? = null

  fun init(renderer: IToastRenderer) {
    toastRenderer = renderer
  }

  fun get(): IToastRenderer {
    val finalToastRenderer = toastRenderer
    if (finalToastRenderer != null) {
      return finalToastRenderer
    }
    val newToastRenderer = getToastRenderer()
    toastRenderer = newToastRenderer
    return newToastRenderer
  }
}
