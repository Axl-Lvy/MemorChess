package proj.memorchess.axl.core.graph

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import proj.memorchess.axl.core.data.DESCENDANT_COUNT_CAP
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.scheduling.CardState

/**
 * Single mutation chokepoint for the opening tree.
 *
 * Persistence is authoritative. The in memory [OpeningTree] is a **bounded, demand paged** cache:
 * it holds only a working set, never the whole repertoire. [node] resolves a position through the
 * cache, falling back to a single [DatabaseQueryManager.getPosition] point lookup on a miss and
 * inserting the rebuilt node into the bounded LRU. On a successful miss it also fires a one ply
 * background prefetch of the node's neighbours so the next navigation step is a cache hit.
 *
 * Mutations write through: they patch the touched cache entries in place and persist, never
 * swapping the whole cache. Exploration moves that have not yet been classified (`isGood == null`)
 * live in the cache only until a caller upserts them with a non null [Edge.isGood].
 *
 * ## Concurrency
 *
 * Background prefetch writes the cache from [Dispatchers.Default] while the UI resolves on the main
 * thread, so every cache read and write funnels through a single [Mutex]. All public suspend
 * methods and the private [warm] take it; the cache is never touched outside that lock.
 *
 * Callers from the UI, interactions and scheduling layers all go through this class.
 *
 * @param database The persistence backend.
 * @param prefetchScope Background scope on which neighbour prefetch runs. A process lived
 *   [kotlinx.coroutines.SupervisorJob] scope on [kotlinx.coroutines.Dispatchers.Default] in
 *   production (a failed prefetch never cancels siblings and never blocks the UI). Tests pass a
 *   deterministic test scope.
 */
class TreeStore(
  private val database: DatabaseQueryManager,
  private val prefetchScope: CoroutineScope,
) {

  private val tree = OpeningTree()
  private val mutex = Mutex()

  /** Keys whose background prefetch is in flight, guarded by [mutex] to dedupe concurrent warms. */
  private val inFlight = mutableSetOf<PositionKey>()

  /**
   * Resolves the node at [positionKey] through the bounded cache.
   *
   * Cache hit returns immediately and marks the entry most recently used. Miss loads the row via
   * [DatabaseQueryManager.getPosition], builds a fully edged [Node] from its incoming and outgoing
   * classified moves, inserts it into the bounded LRU (evicting the least recently used entries
   * past the cap), kicks off one ply neighbour prefetch, and returns it. Returns `null` when the
   * position is not persisted and is not a resident exploration only node.
   */
  suspend fun node(positionKey: PositionKey): Node? {
    val cached = mutex.withLock { tree[positionKey]?.also { tree.touch(positionKey) } }
    if (cached != null) return cached
    val dataNode = database.getPosition(positionKey) ?: return null
    val node = dataNode.toNode()
    mutex.withLock { tree.put(node) }
    prefetchNeighbors(node)
    return node
  }

  /**
   * Computes the [NodeState] for [positionKey] given which position we [arrivedFrom].
   *
   * Resolves the node through [node] then runs the pure incoming edge aggregation. Returns
   * [NodeState.UNKNOWN] when the position cannot be resolved (matching "not in graph" semantics).
   */
  suspend fun computeState(positionKey: PositionKey, arrivedFrom: PositionKey?): NodeState =
    node(positionKey)?.computeState(arrivedFrom) ?: NodeState.UNKNOWN

  /**
   * Depth of [positionKey] resolved through [node], or [Int.MAX_VALUE] when it cannot be resolved.
   */
  suspend fun getDepth(positionKey: PositionKey): Int = node(positionKey)?.depth ?: Int.MAX_VALUE

  /**
   * Counts the non deleted positions a recursive delete starting at [key] would remove, [key]
   * included, bounded by [cap]. Delegates to the backend's bounded breadth first walk so the count
   * never pages the whole subtree through the cache. See [DatabaseQueryManager.countDescendants].
   */
  suspend fun countDescendants(key: PositionKey, cap: Int = DESCENDANT_COUNT_CAP): Int =
    database.countDescendants(key, cap)

  /**
   * Ensures [positionKey] exists in the cache at the given [depth]. No persistence side effect:
   * exploration of a fresh position should not write a row until the user saves something.
   *
   * Synchronous and **not** mutex guarded, so it must only run before any navigation on this store
   * has triggered background prefetch, where it cannot race the prefetch writer. The sole safe
   * caller is a constructor seeding the starting position. Once navigation begins, use
   * [ensurePositionGuarded], which takes the [mutex]; every other cache access goes through [node]
   * under the same lock.
   */
  fun ensurePosition(positionKey: PositionKey, depth: Int) {
    tree.ensure(positionKey, depth)
  }

  /**
   * Ensures [positionKey] exists in the cache at the given [depth], taking the [mutex] so it cannot
   * race a concurrent background prefetch writing the same [OpeningTree]. No persistence side
   * effect. This is the safe variant for any call site reachable after navigation has begun (for
   * example a reset handler), where a [warm] coroutine from an earlier resolve may still be
   * running.
   */
  suspend fun ensurePositionGuarded(positionKey: PositionKey, depth: Int) {
    mutex.withLock { tree.ensure(positionKey, depth) }
  }

  /**
   * Adds or replaces an edge in the graph.
   *
   * Always updates the cache. Persists when [isGood] is not `null`: a classified edge becomes
   * durable, an exploration edge does not. The destination node is created on demand at depth
   * [fromDepth] + 1.
   *
   * When the edge already exists its [Edge.createdAt] is preserved. This is load bearing:
   * exploration replays a line by re-upserting every edge on the way, so a fresh stamp on each
   * upsert would reshuffle the introduction order of new cards just by browsing. The prior edge is
   * resolved through [node] so the persisted, stable [Edge.createdAt] is preserved even when the
   * origin was evicted from the cache.
   *
   * @param from Position the move is played from.
   * @param move SAN of the move.
   * @param to Position reached by playing [move].
   * @param isGood Classification of the move. `null` means exploration only.
   * @param fromDepth Depth of [from] used when the node has to be inserted.
   * @return The [Edge] now present in the cache.
   */
  suspend fun addMove(
    from: PositionKey,
    move: String,
    to: PositionKey,
    isGood: Boolean?,
    fromDepth: Int,
  ): Edge {
    val createdAt = node(from)?.outgoing?.get(move)?.createdAt ?: DateUtil.now()
    val edge =
      Edge(
        from = from,
        move = move,
        to = to,
        isGood = isGood,
        createdAt = createdAt,
        updatedAt = DateUtil.now(),
      )
    mutex.withLock { tree.upsertEdge(edge, fromDepth) }
    if (isGood != null) {
      persistNode(from)
      persistNode(to)
    }
    return edge
  }

  /**
   * Adds every move in [moves] to the cache, then persists all touched nodes in one batch.
   *
   * Behaves like calling [addMove] for each element, except that every node touched by a classified
   * move is written to the database exactly once, through a single
   * [DatabaseQueryManager.insertNodes] call. Existing nodes keep their [CardState]; only their edge
   * maps and, when a shorter path is found, their depth are updated. Each distinct origin's prior
   * edge is resolved through [node] so its [Edge.createdAt] stays stable across re-upserts even
   * when the origin was evicted.
   *
   * @param moves Insertions to apply, in order.
   */
  suspend fun addMoves(moves: List<MoveInsertion>) {
    if (moves.isEmpty()) return
    val now = DateUtil.now()
    val touched = linkedSetOf<PositionKey>()
    for (insertion in moves) {
      val createdAt = node(insertion.from)?.outgoing?.get(insertion.move)?.createdAt ?: now
      val edge =
        Edge(
          from = insertion.from,
          move = insertion.move,
          to = insertion.to,
          isGood = insertion.isGood,
          createdAt = createdAt,
          updatedAt = now,
        )
      mutex.withLock { tree.upsertEdge(edge, insertion.fromDepth) }
      if (insertion.isGood != null) {
        touched += insertion.from
        touched += insertion.to
      }
    }
    val nodesToPersist = mutex.withLock { touched.mapNotNull { tree[it]?.toDataNode() } }
    if (nodesToPersist.isNotEmpty()) {
      database.insertNodes(*nodesToPersist.toTypedArray())
    }
  }

  /**
   * Stores [cardState] on the node at [positionKey] and persists it.
   *
   * Resolves the node through [node]; logs a warning and skips the write when the position cannot
   * be resolved (it was deleted between a reader observing it and writing the result back).
   */
  suspend fun updateCardState(positionKey: PositionKey, cardState: CardState) {
    val existing = node(positionKey)
    if (existing == null) {
      LOGGER.w { "Skipping card state update for unknown position $positionKey" }
      return
    }
    mutex.withLock { tree.put(existing.copy(cardState = cardState)) }
    persistNode(positionKey)
  }

  /**
   * Deletes the move [move] leaving [from] in both the cache and the underlying database.
   *
   * After the cache edge is gone, the surviving [from] node is re-persisted so its derived
   * [DataNode.hasGoodOutgoing] cannot go stale: deleting the last good edge must flip the flag back
   * to `false`.
   */
  suspend fun deleteMove(from: PositionKey, move: String, mode: DeleteMode = DeleteMode.HARD) {
    mutex.withLock { tree.removeEdge(from, move) }
    database.deleteMove(from, move, mode)
    persistNode(from)
  }

  /**
   * Deletes the node at [positionKey] and every incident edge, in both the cache and the database.
   *
   * The target is resolved through [node] so its edge set is available for neighbour patching even
   * when it was evicted from the cache. [DatabaseQueryManager.deletePosition] is authoritative for
   * disk; the cache patches are best effort for resident neighbours.
   */
  suspend fun deleteNode(positionKey: PositionKey, mode: DeleteMode = DeleteMode.HARD) {
    val node = node(positionKey)
    val survivingOrigins = mutableSetOf<PositionKey>()
    if (node != null) {
      mutex.withLock {
        for (edge in node.outgoing.values.toList()) {
          tree.removeEdge(positionKey, edge.move)
        }
        for (edge in node.incoming.values.toList()) {
          tree.removeEdge(edge.from, edge.move)
          survivingOrigins += edge.from
        }
        tree.removeNode(positionKey)
      }
    }
    database.deletePosition(positionKey, mode)
    // Re-persist the origins that lost an outgoing edge so their derived hasGoodOutgoing flag
    // reflects the deletion and cannot go stale.
    for (origin in survivingOrigins) {
      persistNode(origin)
    }
  }

  /** Hard wipes every position and move, both in the cache and on disk. */
  suspend fun eraseAll() {
    database.eraseAll()
    mutex.withLock { tree.clear() }
  }

  /**
   * Persists the cached node at [positionKey], if present. A no-op when the node is gone from the
   * cache (e.g. it was itself just deleted), so it is safe to call after an edge removal to refresh
   * a surviving endpoint's derived [DataNode.hasGoodOutgoing] flag.
   */
  private suspend fun persistNode(positionKey: PositionKey) {
    val node = mutex.withLock { tree[positionKey] } ?: return
    database.insertNodes(node.toDataNode())
  }

  /**
   * Launches a one ply, fire and forget warm of every distinct neighbour of [node]. Neighbours
   * already resident or already in flight are skipped under [mutex]. Prefetch never recurses, so a
   * miss fans out to immediate neighbours and stops, bounded by the branching factor.
   */
  private fun prefetchNeighbors(node: Node) {
    val targets =
      (node.outgoing.values.map { it.to } + node.incoming.values.map { it.from })
        .distinct()
        .filter { it != node.positionKey }
    for (key in targets) {
      prefetchScope.launch { warm(key) }
    }
  }

  /**
   * Loads [key] into the cache if it is neither resident nor already being fetched. Does not
   * recurse into further prefetch (one ply only). The in flight guard and residency check are taken
   * under [mutex] so two concurrent navigations cannot double fetch the same key.
   */
  private suspend fun warm(key: PositionKey) {
    val shouldFetch = mutex.withLock { tree[key] == null && inFlight.add(key) }
    if (!shouldFetch) return
    try {
      val dataNode = database.getPosition(key) ?: return
      mutex.withLock { tree.put(dataNode.toNode()) }
    } finally {
      mutex.withLock { inFlight.remove(key) }
    }
  }
}

/**
 * One move to insert through [TreeStore.addMoves].
 *
 * Mirrors the parameters of [TreeStore.addMove] so a batch element carries exactly the same
 * information as a single insertion.
 *
 * @property from Position the move is played from.
 * @property move SAN of the move.
 * @property to Position reached by playing [move].
 * @property isGood Classification of the move. `null` means exploration only.
 * @property fromDepth Depth of [from] used when the node has to be inserted.
 */
data class MoveInsertion(
  val from: PositionKey,
  val move: String,
  val to: PositionKey,
  val isGood: Boolean?,
  val fromDepth: Int,
)

private fun DataMove.toEdge(): Edge =
  Edge(
    from = origin,
    move = move,
    to = destination,
    isGood = isGood,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
  )

private fun Edge.toDataMove(): DataMove =
  DataMove(
    origin = from,
    destination = to,
    move = move,
    isGood = isGood,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
  )

/**
 * Builds a fully edged [Node] from a persisted [DataNode], exactly as the eager load loop did: non
 * deleted incoming and outgoing moves become [Edge]s. A single point lookup returns both
 * directions, so this rebuilds one node completely.
 */
private fun DataNode.toNode(): Node {
  val outgoing = mutableMapOf<String, Edge>()
  val incoming = mutableMapOf<String, Edge>()
  for (move in previousAndNextMoves.nextMoves.values) {
    if (move.isDeleted) continue
    outgoing[move.move] = move.toEdge()
  }
  for (move in previousAndNextMoves.previousMoves.values) {
    if (move.isDeleted) continue
    incoming[move.move] = move.toEdge()
  }
  return Node(
    positionKey = positionKey,
    outgoing = outgoing,
    incoming = incoming,
    depth = depth,
    cardState = cardState,
  )
}

private fun Node.toDataNode(): DataNode =
  DataNode(
    positionKey = positionKey,
    previousAndNextMoves =
      PreviousAndNextMoves(
        previousMoves =
          incoming.values.filter { it.isGood != null && !it.isDeleted }.map { it.toDataMove() },
        nextMoves =
          outgoing.values.filter { it.isGood != null && !it.isDeleted }.map { it.toDataMove() },
      ),
    cardState = cardState,
    depth = depth,
    hasGoodOutgoing = outgoing.values.any { it.isGood == true && !it.isDeleted },
    createdAt =
      incoming.values.filter { !it.isDeleted }.minOfOrNull { it.createdAt } ?: DateUtil.now(),
    updatedAt = DateUtil.now(),
  )

private val LOGGER = Logger.withTag("TreeStore")
