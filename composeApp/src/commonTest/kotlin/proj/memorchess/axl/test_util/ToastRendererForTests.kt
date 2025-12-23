package proj.memorchess.axl.test_util

import proj.memorchess.axl.ui.components.popup.ToastRenderer
import proj.memorchess.axl.ui.components.popup.ToastType

/** Toast renderer for tests that save toasted messages. */
object ToastRendererForTests : ToastRenderer {

  /** List of toasted messages with their types. */
  val messages = mutableListOf<Pair<ToastType, String>>()

  override fun toast(message: String, type: ToastType) {
    this.messages.add(type to message)
  }

  /** Clears the saved messages. */
  fun clear() {
    messages.clear()
  }
}
