package proj.memorchess.axl.test_util

import proj.memorchess.axl.core.util.Reloader

object NoOpReloader : Reloader {
  override fun reload() {
    // No operation
  }

  override fun getKey(): Any {
    return false
  }
}
