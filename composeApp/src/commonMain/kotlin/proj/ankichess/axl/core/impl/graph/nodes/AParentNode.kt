package proj.ankichess.axl.core.impl.graph.nodes

import proj.ankichess.axl.core.impl.data.StoredPosition
import proj.ankichess.axl.core.intf.data.IStoredPosition
import proj.ankichess.axl.core.intf.engine.board.IPosition
import proj.ankichess.axl.core.intf.graph.INode

/**
 * Node that has next.
 *
 * @constructor Create empty A parent node
 */
abstract class AParentNode(position: IPosition) : ASavableNode(position) {

  private var firstChild: INode? = null

  override fun addChild(move: String, node: INode) {
    firstChild = node
    children[move] = node
  }

  private val children = mutableMapOf<String, INode>()

  override fun getChildren(): MutableMap<String, INode> {
    return children
  }

  override fun getFirstChild(): INode? {
    return firstChild
  }

  override fun getPosition(): IStoredPosition {
    return StoredPosition(position.toString(), children.keys.toList())
  }
}
