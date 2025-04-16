package proj.ankichess.axl.core.impl.graph.nodes

import proj.ankichess.axl.core.intf.graph.INode

class OrphanNode(position: String) : AParentNode(position) {

  override fun getParent(): INode? {
    return null
  }
}
