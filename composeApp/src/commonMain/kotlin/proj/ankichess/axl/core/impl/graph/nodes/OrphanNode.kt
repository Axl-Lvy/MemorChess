package proj.ankichess.axl.core.impl.graph.nodes

import proj.ankichess.axl.core.intf.engine.board.IPosition
import proj.ankichess.axl.core.intf.graph.INode

class OrphanNode(position: IPosition) : AParentNode(position) {

  override fun getParent(): INode? {
    return null
  }
}
