package proj.ankichess.axl.core.impl.graph.nodes

import proj.ankichess.axl.core.impl.engine.Game
import proj.ankichess.axl.core.impl.engine.parser.FenParser
import proj.ankichess.axl.core.intf.graph.INode

/**
 * Node that has next.
 *
 * @constructor Create empty A parent node
 */
abstract class AParentNode(private val position: String) : ASavableNode() {

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

  override fun getGame(): Game {
    return Game(FenParser.readPosition(position))
  }
}
