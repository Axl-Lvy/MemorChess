package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.engine.Game

/** Main graph component. */
interface INode {

  /**
   * Nodes that have this as next.
   *
   * @return Previous nodes.
   */
  fun getParent(): INode?

  /**
   * Possible next nodes.
   *
   * @return Next nodes.
   */
  fun getChildren(): Map<String, INode>

  /**
   * Adds a child to this node.
   *
   * @param move Move that leaded to this node.
   * @param node Node that is the child of this node.
   */
  fun addChild(move: String, node: INode)

  /**
   * The next node.
   *
   * @return Next node.
   */
  fun getFirstChild(): INode?

  /** True if this node is saved. */
  fun isSaved(): Boolean

  /** Saves this node. */
  suspend fun save()

  fun getGame(): Game
}
