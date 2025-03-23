package proj.ankichess.axl.core.graph.nodes

/**
 * Main node.
 *
 * @param parent Parent node.
 * @param move Move that leaded to this node.
 * @constructor Creates a node from a parent.
 */
class Node(parent: INode, move: String) : AParentNode() {

  private val parents = mutableMapOf<String, INode>()

  init {
    parents[move] = parent
  }

  override fun getParents(): Map<String, INode> {
    return parents
  }
}
