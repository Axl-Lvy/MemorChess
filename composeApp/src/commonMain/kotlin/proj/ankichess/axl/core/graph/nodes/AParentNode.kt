package proj.ankichess.axl.core.graph.nodes

/**
 * Node that has next.
 *
 * @constructor Create empty A parent node
 */
abstract class AParentNode : INode {
  private val children = mutableMapOf<String, INode>()

  override fun getChildren(): Map<String, INode> {
    return children
  }
}
