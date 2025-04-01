package proj.ankichess.axl.core.impl.graph.nodes

import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.intf.graph.INode

/** Root node. It represents the starting position. */
class RootNode(position: String) : AParentNode(position) {

  constructor() : this(NodeFactory.createKey(Game()))

  override fun getParent(): INode? {
    return null
  }
}
