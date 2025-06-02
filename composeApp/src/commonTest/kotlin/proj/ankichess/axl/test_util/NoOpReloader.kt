package proj.ankichess.axl.test_util

import proj.ankichess.axl.core.intf.util.IReloader

object NoOpReloader : IReloader {
  override fun reload() {
    // No operation
  }

  override fun getKey(): Any {
    return false
  }
}
