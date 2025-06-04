package proj.memorchess.axl.ui.util.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import proj.memorchess.axl.core.util.IReloader

/**
 * Basic implementation of [IReloader].
 *
 * @constructor Create a reloader
 */
class BasicReloader : IReloader {

  private var key by mutableStateOf(false)

  override fun reload() {
    key = !key
  }

  override fun getKey(): Any {
    return key
  }
}
