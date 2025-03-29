package proj.ankichess.axl.core.graph.nodes

/**
 * Main node.
 *
 * @param parent Parent node.
 * @param move Move that leaded to this node.
 * @param position Position.
 * @constructor Creates a node from a parent.
 */
class Node(private val parent: INode, private val move: String?, position: String) :
  AParentNode(position) {

  override fun getParent(): INode {
    return parent
  }

  override fun save() {
    super.save()
    if (move != null && !parent.getChildren().containsKey(move)) {
      parent.getChildren()[move] = this
      parent.save()
    }
  }
}
