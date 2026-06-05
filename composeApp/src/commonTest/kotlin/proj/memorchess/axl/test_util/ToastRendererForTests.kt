package proj.memorchess.axl.test_util

import org.jetbrains.compose.resources.StringResource
import proj.memorchess.axl.ui.components.popup.ToastRenderer
import proj.memorchess.axl.ui.components.popup.ToastType

/** Toast renderer for tests that save toasted messages. */
object ToastRendererForTests : ToastRenderer {

  /** List of toasted messages with their types. */
  val messages = mutableListOf<Pair<ToastType, StringResource>>()

  override fun toast(message: StringResource, type: ToastType) {
    this.messages.add(type to message)
  }

  /** Clears the saved messages. */
  fun clear() {
    messages.clear()
  }
}
