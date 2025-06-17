package proj.memorchess.axl.ui.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import proj.memorchess.axl.core.util.IReloader

/**
 * A reloader that calls a callback function AFTER reloading.
 *
 * @property callback The function to call AFTER reloading.
 */
class CallbackReloader(private val callback: () -> Any) : IReloader {

  private var key by mutableStateOf(false)

  override fun reload() {
    key = !key
    callback()
  }

  override fun getKey(): Any {
    return key
  }
}
