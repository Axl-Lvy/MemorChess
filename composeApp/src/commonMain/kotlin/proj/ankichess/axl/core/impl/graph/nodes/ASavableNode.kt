package proj.ankichess.axl.core.impl.graph.nodes

import proj.ankichess.axl.core.intf.graph.INode

abstract class ASavableNode : INode {
  private var isSaved = false

  override fun isSaved(): Boolean {
    return isSaved
  }

  override fun save() {
    isSaved = true
  }
}
