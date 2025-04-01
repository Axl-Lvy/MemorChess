package proj.ankichess.axl.core.intf.graph

import proj.ankichess.axl.core.impl.engine.Game

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
  fun getChildren(): MutableMap<String, INode>

  /** Saves this node. */
  fun save()

  fun getGame(): Game
}
