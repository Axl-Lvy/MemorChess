package proj.ankichess.axl.core.impl.graph.nodes

import proj.ankichess.axl.core.impl.engine.board.Position
import proj.ankichess.axl.core.intf.graph.INode

/** Root node. It represents the starting position. */
class RootNode : AParentNode(Position()) {

  override fun getParent(): INode? {
    return null
  }
}
