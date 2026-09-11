package proj.memorchess.axl.core.graph

import co.touchlab.kermit.Logger
import proj.memorchess.axl.core.data.DESCENDANT_COUNT_CAP
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.DirtyKey
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.scheduling.CardState
import proj.memorchess.axl.core.sync.DeviceIdentity
import proj.memorchess.axl.core.sync.resolve
import proj.memorchess.axl.core.sync.toDataMove
import proj.memorchess.axl.core.sync.toDataNode

/**
 * Single chokepoint for local mutation of the opening tree. Remote writes land through
 * [proj.memorchess.axl.core.sync.SyncApplier] instead.
 *
 * Persistence is authoritative. [NodeCache] holds only a bounded working set, never the whole
 * repertoire. [node] resolves a position through it, and on a miss also fires a one ply background
 * prefetch of the node's neighbours so the next navigation step is a cache hit.
 *
 * Mutations write through: they patch the touched cache entries in place and persist, never
 * swapping the whole cache. Exploration moves that have not yet been classified (`isGood == null`)
 * live in the cache only until a caller upserts them with a non null [Edge.isGood].
 *
 * ## Concurrency
 *
 * The facade holds no lock of its own. Every cache read and write goes through [NodeCache], which
 * owns the mutex and dedupes concurrent loads of the same key, and every background warm goes
 * through [Prefetcher]. A mutation's edits survive a load of the same key that is already in
 * flight; see the contract on [NodeCache.resolve].
 *
 * Callers from the UI, interactions and scheduling layers all go through this class.
 *
 * @param database The persistence backend.
 * @param cache Bounded cache every read and write of the graph goes through.
 * @param prefetcher Fires the one ply neighbour warm after a miss.
 * @param trainable Per repertoire trainable projection, recomputed after every edge write.
 * @param tagStore Used by the delete paths to tombstone a removed edge's repertoire tags.
 * @param deviceIdentity Stamped onto every persisted node and edge, and used to order this device's
 *   own writes against its earlier ones. See [DeviceIdentity].
 * @param notifyDirty Called after every local write that queues an outbox entry, so
 *   [proj.memorchess.axl.core.sync.SyncEngine] can schedule a push. Never called from
 *   [proj.memorchess.axl.core.sync.SyncApplier], whose writes are remote in origin.
 */
class TreeStore(
  private val database: DatabaseQueryManager,
  private val cache: NodeCache,
  private val prefetcher: Prefetcher,
  private val trainable: TrainableProjection,
  private val tagStore: RepertoireTagStore,
  private val deviceIdentity: DeviceIdentity,
  private val notifyDirty: () -> Unit = {},
) {

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
    val (node, hit) = cache.resolve(positionKey)
    if (!hit && node != null) prefetcher.warmNeighbors(node)
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
   * Synchronous and **not** lock guarded, so it must only run before any navigation on this store
   * has triggered a load, where it cannot race the loading writer. The sole safe caller is a
   * constructor seeding the starting position. Once navigation begins, use [ensurePositionGuarded].
   */
  fun ensurePosition(positionKey: PositionKey, depth: Int) {
    cache.ensureUnlocked(positionKey, depth)
  }

  /**
   * Ensures [positionKey] exists in the cache at the given [depth], under the cache's own lock so
   * it cannot race a concurrent load. No persistence side effect. This is the safe variant for any
   * call site reachable after navigation has begun, for example a reset handler.
   */
  suspend fun ensurePositionGuarded(positionKey: PositionKey, depth: Int) {
    cache.ensure(positionKey, depth)
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
    // TODO: nextDeviceSeq is allocated here even for an exploration-only move (isGood == null,
    // never persisted), one at a time. A block-allocation batching fix is a larger change; see the
    // sync design doc.
    val edge =
      Edge(
        from = from,
        move = move,
        to = to,
        isGood = isGood,
        createdAt = createdAt,
        updatedAt = DateUtil.now(),
        originDevice = deviceIdentity.originDevice,
        deviceSeq = deviceIdentity.nextDeviceSeq(),
      )
    cache.upsertEdge(edge, fromDepth)
    if (isGood != null) {
      persistNode(from)
      persistNode(to)
      // The two persists above each queue their own row's outbox entry transactionally. The edge
      // itself is not a row either of them can attribute a stamp to (it is a field inside each
      // node's persisted move maps), so it is marked separately here, at the edge's own deviceSeq.
      database.markDirty(DirtyKey.EdgeKey(from, to), edge.deviceSeq)
      // to's own trainable membership depends only on its outgoing edges, which this call did not
      // change, so only from needs recomputing.
      trainable.recompute(from)
      notifyDirty()
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
    val dirtyEdges = mutableListOf<Pair<DirtyKey.EdgeKey, Long>>()
    // TODO: nextDeviceSeq below is allocated per edge, one at a time, even for exploration-only
    // moves (isGood == null, never persisted). A block-allocation batching fix is a larger change;
    // see the sync design doc.
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
          originDevice = deviceIdentity.originDevice,
          deviceSeq = deviceIdentity.nextDeviceSeq(),
        )
      cache.upsertEdge(edge, insertion.fromDepth)
      if (insertion.isGood != null) {
        touched += insertion.from
        touched += insertion.to
        dirtyEdges += DirtyKey.EdgeKey(insertion.from, insertion.to) to edge.deviceSeq
      }
    }
    val nodesToPersist = touched.mapNotNull { cache.peek(it)?.toDataNode() }
    if (nodesToPersist.isNotEmpty()) {
      // insertNodes queues each node's own outbox entry transactionally; only the edges themselves
      // need marking here, same as addMove.
      database.insertNodes(*nodesToPersist.toTypedArray())
      for ((edgeKey, seq) in dirtyEdges) database.markDirty(edgeKey, seq)
      // A freshly created destination has no outgoing edges yet, so recomputing it too is a
      // harmless no-op that resolves to an empty set.
      for (origin in touched) trainable.recompute(origin)
      notifyDirty()
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
    cache.put(existing.copy(cardState = cardState))
    // persistNode below queues the node's own outbox entry transactionally with the row write.
    persistNode(positionKey)
    // The edge set does not change here, but NodeRepertoireTrainable.lastReview must still track
    // the position's latest review.
    trainable.recompute(positionKey)
    notifyDirty()
  }

  /**
   * Deletes the move [move] leaving [from] in both the cache and the underlying database.
   *
   * After the cache edge is gone, the surviving [from] node is re-persisted so its derived
   * [DataNode.hasGoodOutgoing] cannot go stale: deleting the last good edge must flip the flag back
   * to `false`. The edge and the surviving [from] node are each marked dirty by the
   * [DatabaseQueryManager] call that writes their row, in the same transaction as that write.
   */
  suspend fun deleteMove(from: PositionKey, move: String, mode: DeleteMode = DeleteMode.SOFT) {
    // Resolved through node() so from is resident even when it was evicted from the cache: the
    // follow up persistNode(from) below is a documented no-op on a cache miss, so without this,
    // an evicted from's hasGoodOutgoing would go stale and never get queued.
    val destination = node(from)?.outgoing?.get(move)?.to
    cache.removeEdge(from, move, destination)
    val seq = deviceIdentity.nextDeviceSeq()
    // deleteMove queues the edge's own outbox entry transactionally with the tombstone (SOFT only);
    // persistNode below queues the surviving from node's own entry transactionally with its
    // re-derived hasGoodOutgoing.
    database.deleteMove(from, move, mode, deviceIdentity.originDevice, seq, DateUtil.now())
    persistNode(from)
    // Tombstoning the tags before recomputing means the now deleted edge's tags are excluded from
    // the freshly recomputed set even though (for mode == SOFT) the row itself may still be
    // resolvable for one more tick.
    if (destination != null) tagStore.tombstoneTags(from, destination)
    trainable.recompute(from)
    notifyDirty()
  }

  /**
   * Deletes the node at [positionKey] and every incident edge, in both the cache and the database.
   *
   * The target is resolved through [node] so its edge set is available for neighbour patching even
   * when it was evicted from the cache. [DatabaseQueryManager.deletePosition] is authoritative for
   * disk; the cache patches are best effort for resident neighbours. [positionKey], every incident
   * edge it tombstones, and every surviving origin re-persisted below are each marked dirty by the
   * [DatabaseQueryManager] call that writes their row, in the same transaction as that write.
   */
  suspend fun deleteNode(positionKey: PositionKey, mode: DeleteMode = DeleteMode.SOFT) {
    val node = node(positionKey)
    val survivingOrigins = mutableSetOf<PositionKey>()
    if (node != null) {
      for (edge in node.outgoing.values) tagStore.tombstoneTags(positionKey, edge.to)
      for (edge in node.incoming.values) tagStore.tombstoneTags(edge.from, positionKey)
    }
    val seq = deviceIdentity.nextDeviceSeq()
    // The durable write sits ahead of the cache patching below, so the invalidate that drops
    // positionKey reflects a row that is already gone. A stale mark set before its own write would
    // have the retry read the same superseded row twice.
    database.deletePosition(positionKey, mode, deviceIdentity.originDevice, seq, DateUtil.now())
    trainable.clear(positionKey)
    if (node != null) {
      for (edge in node.outgoing.values.toList()) {
        cache.removeEdge(positionKey, edge.move, edge.to)
      }
      for (edge in node.incoming.values.toList()) {
        cache.removeEdge(edge.from, edge.move, positionKey)
        survivingOrigins += edge.from
      }
      cache.invalidate(positionKey)
    }
    // Re-persist the origins that lost an outgoing edge so their derived hasGoodOutgoing flag
    // reflects the deletion and cannot go stale.
    for (origin in survivingOrigins) {
      persistNode(origin)
      trainable.recompute(origin)
    }
    notifyDirty()
  }

  /** Hard wipes every position and move, both in the cache and on disk. */
  suspend fun eraseAll() {
    database.eraseAll()
    cache.clear()
  }

  /**
   * Persists the cached node at [positionKey], if present. A no-op when the node is gone from the
   * cache (e.g. it was itself just deleted), so it is safe to call after an edge removal to refresh
   * a surviving endpoint's derived [DataNode.hasGoodOutgoing] flag.
   */
  private suspend fun persistNode(positionKey: PositionKey) {
    val node = cache.peek(positionKey) ?: return
    database.insertNodes(node.toDataNode())
  }

  /**
   * Builds the [DataNode] to persist for this cached [Node], stamping a fresh [DeviceIdentity]
   * sequence on every call. A member function (not top level like [DataMove.toEdge] and
   * [Edge.toDataMove]) because, unlike [Edge], [Node] carries no `updatedAt`/`originDevice`
   * /`deviceSeq` of its own: every persist derives them fresh from [deviceIdentity], the same way
   * [updatedAt] already does.
   */
  private suspend fun Node.toDataNode(): DataNode =
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
      originDevice = deviceIdentity.originDevice,
      deviceSeq = deviceIdentity.nextDeviceSeq(),
    )
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

private fun Edge.toDataMove(): DataMove =
  DataMove(
    origin = from,
    destination = to,
    move = move,
    isGood = isGood,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    originDevice = originDevice,
    deviceSeq = deviceSeq,
  )

private val LOGGER = Logger.withTag("TreeStore")
