package proj.ankichess.axl.ui.popup

object PopupRendererHolder {
  private var popupRenderer: IPopupRenderer? = null

  fun init(renderer: IPopupRenderer) {
    popupRenderer = renderer
  }

  fun get(): IPopupRenderer {
    val finalPopupRenderer = this.popupRenderer
    if (finalPopupRenderer != null) {
      return finalPopupRenderer
    }
    val newPopupRenderer = getPopupRenderer()
    this.popupRenderer = newPopupRenderer
    return newPopupRenderer
  }
}
