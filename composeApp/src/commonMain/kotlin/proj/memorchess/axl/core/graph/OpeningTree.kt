package proj.memorchess.axl.core.graph

import proj.memorchess.axl.core.data.PositionKey

/**
 * Bounded, demand paged cache of chess positions keyed by [PositionKey].
 *
 * The tree only knows about [Node] and [Edge]. It has no persistence, no scheduling and no I/O. It
 * is a **partial** view of the repertoire: under demand paging only a bounded working set of nodes
 * is resident at any moment. Code must never assume the whole graph is in memory; an absent key is
 * a cache miss that [TreeStore] resolves on demand from the database, not a proof the position does
 * not exist.
 *
 * ## Eviction
 *
 * The cache keeps at most [MAX_CACHE_NODES] entries with a least recently used policy. Recency is
 * maintained explicitly because Kotlin Multiplatform does not expose an access ordered
 * [LinkedHashMap] on every target: [touch] (and every write) removes then re puts a key so the most
 * recently used entry sits at the tail of the insertion order, and eviction drops from the head.
 *
 * Eviction never corrupts a still resident node. Edges are duplicated by value into both endpoints
 * (see [Edge]), so dropping node `X` only removes `X`'s own [Node] object; a resident neighbour `Y`
 * still holds its copy of any shared edge in its own [Node.outgoing] / [Node.incoming]. `X` is
 * rebuilt from a point lookup the next time it is resolved.
 *
 * ## Thread safety
 *
 * This class is **not** thread safe on its own. Background neighbour prefetch writes the cache
 * concurrently with main thread resolves, so [TreeStore] funnels every read and write through a
 * single mutex. Do not mutate the cache from outside [TreeStore].
 *
 * All mutators are package internal. Production code only mutates the tree through [TreeStore].
 */
class OpeningTree {

  private val positions = LinkedHashMap<PositionKey, Node>()

  /**
   * Returns the [Node] for [positionKey], or `null` when the position is not resident in the cache.
   *
   * A `null` result is a cache miss, not a proof of absence: under demand paging the position may
   * exist on disk but not be loaded. Pure read; does not update recency. Use [touch] (via
   * [TreeStore]) to mark an entry most recently used.
   */
  operator fun get(positionKey: PositionKey): Node? = positions[positionKey]

  /** Number of entries currently resident in the cache. Used by tests asserting eviction. */
  internal fun residentCount(): Int = positions.size

  // --- Package internal mutators. Callers must route through TreeStore. ---

  /** Empties the cache. Used only by [TreeStore.eraseAll]. */
  internal fun clear() {
    positions.clear()
  }

  /**
   * Marks [positionKey] most recently used by moving it to the tail of the recency order. No op
   * when the key is not resident.
   */
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

  /**
   * Inserts or replaces the node at [Node.positionKey], marks it most recently used, and evicts the
   * least recently used entries until the cache is back within [MAX_CACHE_NODES].
   */
  internal fun put(node: Node) {
    positions.remove(node.positionKey)
    positions[node.positionKey] = node
    evictIfNeeded()
  }

  /** Removes the node at [positionKey], if any. Does not touch other nodes' edges. */
  internal fun removeNode(positionKey: PositionKey) {
    positions.remove(positionKey)
  }

  /**
   * Adds or replaces [edge] in the cache, updating the endpoint nodes' [Node.outgoing] and
   * [Node.incoming] maps in lockstep. Nodes are created on demand at [fromDepth] and [fromDepth] +
   * 1 when missing. Both endpoints are marked most recently used.
   */
  internal fun upsertEdge(edge: Edge, fromDepth: Int) {
    val from = ensure(edge.from, fromDepth)
    val to = ensure(edge.to, fromDepth + 1)
    put(from.copy(outgoing = from.outgoing + (edge.move to edge)))
    put(to.copy(incoming = to.incoming + (edge.move to edge)))
  }

  /**
   * Removes the edge identified by [from] + [move] from both resident endpoints. The endpoint nodes
   * themselves remain in the cache. A no op for a non resident endpoint: the database delete is
   * authoritative and a later resolve rebuilds that node without the edge.
   */
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
    /**
     * Maximum number of resident nodes. One training or exploration session navigates a handful of
     * plies times the branching factor, far below this cap, so navigation stays a cache hit while
     * memory remains bounded regardless of repertoire size.
     */
    const val MAX_CACHE_NODES: Int = 512
  }
}
