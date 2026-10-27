package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.PositionKey

/**
 * Bounded LRU cache of positions, a partial view of the repertoire where an absent key is a miss,
 * not proof the position does not exist. Not thread safe: mutate it only through [NodeCache].
 */
class OpeningTree {

  // Insertion ordered with re-puts on access: KMP has no access ordered LinkedHashMap everywhere.
  private val positions = LinkedHashMap<PositionKey, Node>()

  /** The resident [Node] for [positionKey], or `null` on a cache miss; does not update recency. */
  operator fun get(positionKey: PositionKey): Node? = positions[positionKey]

  /** Number of entries currently resident in the cache. */
  internal fun residentCount(): Int = positions.size

  /** Empties the cache. */
  internal fun clear() {
    positions.clear()
  }

  /** Marks [positionKey] most recently used; no op when it is not resident. */
  internal fun touch(positionKey: PositionKey) {
    val existing = positions.remove(positionKey) ?: return
    positions[positionKey] = existing
  }

  /** Returns the node at [positionKey], creating an empty one with the given [depth] if missing. */
  internal fun ensure(positionKey: PositionKey, depth: Int): Node {
    val existing = positions[positionKey]
    if (existing != null) {
      return if (depth < existing.depth) {
        existing.copy(depth = depth).also { put(it) }
      } else {
        touch(positionKey)
        existing
      }
    }
    val created = Node(positionKey = positionKey, depth = depth)
    put(created)
    return created
  }

  /** Inserts or replaces [node] as most recently used, evicting down to [MAX_CACHE_NODES]. */
  internal fun put(node: Node) {
    positions.remove(node.positionKey)
    positions[node.positionKey] = node
    evictIfNeeded()
  }

  /** Removes the node at [positionKey], if any. Does not touch other nodes' edges. */
  internal fun removeNode(positionKey: PositionKey) {
    positions.remove(positionKey)
  }

  /** Adds or replaces [edge] in both endpoints, creating them at [fromDepth] and the next depth. */
  internal fun upsertEdge(edge: Edge, fromDepth: Int) {
    val from = ensure(edge.from, fromDepth)
    val to = ensure(edge.to, fromDepth + 1)
    put(from.copy(outgoing = from.outgoing + (edge.move to edge)))
    put(to.copy(incoming = to.incoming + (edge.move to edge)))
  }

  /** Removes the edge [from] + [move] from both resident endpoints; no op when [from] is absent. */
  internal fun removeEdge(from: PositionKey, move: String) {
    val originNode = positions[from] ?: return
    val edge = originNode.outgoing[move] ?: return
    put(originNode.copy(outgoing = originNode.outgoing - move))
    val destinationNode = positions[edge.to]
    if (destinationNode != null) {
      put(destinationNode.copy(incoming = destinationNode.incoming - move))
    }
  }

  private fun evictIfNeeded() {
    while (positions.size > MAX_CACHE_NODES) {
      val oldest = positions.keys.iterator().next()
      positions.remove(oldest)
    }
  }

  companion object {
    /** Maximum number of resident nodes, far above what one session navigates. */
    const val MAX_CACHE_NODES: Int = 512
  }
}
