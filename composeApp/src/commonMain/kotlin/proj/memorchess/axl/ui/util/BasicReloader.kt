package proj.memorchess.axl.ui.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import proj.memorchess.axl.core.util.Reloader

/** Basic implementation of [Reloader]. */
class BasicReloader : Reloader {

  private var key by mutableStateOf(1)

  override fun reload() {
    key = key + 1
  }

  override fun getKey(): Any {
    return key
  }
}
