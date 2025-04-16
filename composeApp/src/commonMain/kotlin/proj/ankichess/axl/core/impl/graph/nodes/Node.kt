package proj.ankichess.axl.core.impl.graph.nodes

import proj.ankichess.axl.core.intf.engine.board.IPosition
import proj.ankichess.axl.core.intf.graph.INode

/**
 * Main node.
 *
 * @param parent Parent node.
 * @param position Position.
 * @constructor Creates a node from a parent.
 */
class Node(private val parent: INode, position: IPosition) : AParentNode(position) {

  override fun getParent(): INode {
    return parent
  }

  override suspend fun save() {
    super.save()
    if (!parent.isSaved()) {
      parent.save()
    }
  }
}
