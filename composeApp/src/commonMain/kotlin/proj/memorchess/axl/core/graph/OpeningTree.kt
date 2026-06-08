package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.PositionKey

/**
 * Pure in memory graph of chess positions keyed by [PositionKey].
 *
 * The tree only knows about [Node] and [Edge]. It has no persistence, no scheduling and no I/O.
 * Every mutation produces a new immutable [Node]; the tree's internal map is replaced atomically
 * via [snapshot] and [replaceFrom] when callers want to swap in a freshly loaded graph.
 *
 * All mutators are package internal. Production code only mutates the tree through
 * [TreeStore][TreeStore]. Tests should also go through [TreeStore] to keep persistence and the
 * cache in sync.
 */
class OpeningTree {

  private var positions: Map<PositionKey, Node> = emptyMap()

  /** Returns the [Node] for [positionKey], or `null` when the position is not in the graph. */
  operator fun get(positionKey: PositionKey): Node? = positions[positionKey]

  /**
   * Returns the depth of [positionKey], or [Int.MAX_VALUE] when the position is not in the graph.
   */
  fun getDepth(positionKey: PositionKey): Int = positions[positionKey]?.depth ?: Int.MAX_VALUE

  /** Checks whether [positionKey] is in the graph. */
  fun isKnown(positionKey: PositionKey): Boolean = positionKey in positions

  /** Returns an immutable snapshot of every node currently in the graph. */
  fun snapshot(): Map<PositionKey, Node> = positions

  /**
   * Counts positions in the subtree rooted at [positionKey] that would be deleted in a recursive
   * delete. A child is only counted if it has no other parents.
   */
  fun countDescendants(positionKey: PositionKey, viaMove: String? = null): Int {
    val node = positions[positionKey] ?: return 0
    if (viaMove != null && node.incoming.size > 1) {
      return 0
    }
    var count = 1
    node.outgoing.values.forEach { edge -> count += countDescendants(edge.to, edge.move) }
    return count
  }

  /**
   * Computes the [NodeState] for [positionKey] given which position we arrived from.
   *
   * @param positionKey Position to compute the state for.
   * @param arrivedFrom Origin position the user came from, or `null` when no path is tracked.
   */
  fun computeState(positionKey: PositionKey, arrivedFrom: PositionKey?): NodeState {
    val node = positions[positionKey] ?: return NodeState.UNKNOWN
    val incoming = node.incoming
    if (incoming.isEmpty()) return NodeState.FIRST

    var aggregateGood: Boolean? = null
    var arrivalGood: Boolean? = null
    for (edge in incoming.values) {
      when (edge.isGood) {
        true -> {
          if (aggregateGood == false) return NodeState.BAD_STATE
          aggregateGood = true
        }
        false -> {
          if (aggregateGood == true) return NodeState.BAD_STATE
          aggregateGood = false
        }
        null -> Unit
      }
      if (arrivedFrom != null && arrivedFrom == edge.from) {
        arrivalGood = edge.isGood
      }
    }
    return determineState(aggregateGood, arrivalGood)
  }

  // --- Package internal mutators. Callers must route through TreeStore. ---

  /**
   * Replaces the entire graph with [newPositions]. Used to swap in a freshly rebuilt cache without
   * exposing intermediate states.
   */
  internal fun replaceFrom(newPositions: Map<PositionKey, Node>) {
    positions = newPositions
  }

  /** Empties the graph. */
  internal fun clear() {
    positions = emptyMap()
  }

  /** Returns the node at [positionKey], creating an empty one with the given [depth] if missing. */
  internal fun ensure(positionKey: PositionKey, depth: Int): Node {
    val existing = positions[positionKey]
    if (existing != null) {
      return if (depth < existing.depth) {
        existing.copy(depth = depth).also { positions = positions + (positionKey to it) }
      } else {
        existing
      }
    }
    val created = Node(positionKey = positionKey, depth = depth)
    positions = positions + (positionKey to created)
    return created
  }

  /** Inserts or replaces the node at [Node.positionKey]. */
  internal fun put(node: Node) {
    positions = positions + (node.positionKey to node)
  }

  /** Removes the node at [positionKey], if any. Does not touch other nodes' edges. */
  internal fun removeNode(positionKey: PositionKey) {
    positions = positions - positionKey
  }

  /**
   * Adds or replaces [edge] in the graph, updating the endpoint nodes' [Node.outgoing] and
   * [Node.incoming] maps in lockstep. Nodes are created on demand at [fromDepth] and [fromDepth] +
   * 1 when missing.
   */
  internal fun upsertEdge(edge: Edge, fromDepth: Int) {
    val from = ensure(edge.from, fromDepth)
    val to = ensure(edge.to, fromDepth + 1)
    val newFrom = from.copy(outgoing = from.outgoing + (edge.move to edge))
    val newTo = to.copy(incoming = to.incoming + (edge.move to edge))
    positions = positions + mapOf(edge.from to newFrom, edge.to to newTo)
  }

  /**
   * Removes the edge identified by [from] + [move] from both endpoints. The endpoint nodes
   * themselves remain in the graph.
   */
  internal fun removeEdge(from: PositionKey, move: String) {
    val originNode = positions[from] ?: return
    val edge = originNode.outgoing[move] ?: return
    val updates = mutableMapOf<PositionKey, Node>()
    updates[from] = originNode.copy(outgoing = originNode.outgoing - move)
    val destinationNode = positions[edge.to]
    if (destinationNode != null) {
      updates[edge.to] = destinationNode.copy(incoming = destinationNode.incoming - move)
    }
    positions = positions + updates
  }

  private fun determineState(aggregateGood: Boolean?, arrivalGood: Boolean?): NodeState =
    when (aggregateGood) {
      null ->
        when (arrivalGood) {
          null -> NodeState.UNKNOWN
          else -> NodeState.BAD_STATE
        }
      true ->
        when (arrivalGood) {
          null -> NodeState.SAVED_GOOD_BUT_UNKNOWN_MOVE
          true -> NodeState.SAVED_GOOD
          false -> NodeState.BAD_STATE
        }
      false ->
        when (arrivalGood) {
          null -> NodeState.SAVED_BAD_BUT_UNKNOWN_MOVE
          true -> NodeState.BAD_STATE
          false -> NodeState.SAVED_BAD
        }
    }
}
