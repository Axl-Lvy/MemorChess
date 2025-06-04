package proj.memorchess.axl.test_util

import proj.memorchess.axl.core.util.IReloader

object NoOpReloader : IReloader {
  override fun reload() {
    // No operation
  }

  override fun getKey(): Any {
    return false
  }
}
