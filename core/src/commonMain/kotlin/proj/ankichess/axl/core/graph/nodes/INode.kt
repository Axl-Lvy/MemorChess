package proj.ankichess.axl.core.graph.nodes

/** Main graph component. */
interface INode {

  /**
   * Nodes that have this as next.
   *
   * @return Previous nodes.
   */
  fun getParents(): Map<String, INode>

  /**
   * Possible next nodes.
   *
   * @return Next nodes.
   */
  fun getChildren(): Map<String, INode>
}
