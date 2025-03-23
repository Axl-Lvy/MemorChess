package proj.ankichess.axl.core.graph.nodes

/** Root node. It represents the starting position. */
class RootNode : AParentNode() {

  override fun getParents(): Map<String, INode> {
    return emptyMap()
  }
}
