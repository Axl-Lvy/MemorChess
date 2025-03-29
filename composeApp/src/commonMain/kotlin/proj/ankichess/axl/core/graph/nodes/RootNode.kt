package proj.ankichess.axl.core.graph.nodes

import proj.ankichess.axl.core.engine.Game

/** Root node. It represents the starting position. */
class RootNode(position: String) : AParentNode(position) {

  constructor() : this(NodeFactory.createKey(Game()))

  override fun getParent(): INode? {
    return null
  }
}
