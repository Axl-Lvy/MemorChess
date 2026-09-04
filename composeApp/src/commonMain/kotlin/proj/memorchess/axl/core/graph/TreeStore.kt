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
import proj.memorchess.axl.core.data.DirtyKey
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.scheduling.CardState
import proj.memorchess.axl.core.sync.DeviceIdentity
import proj.memorchess.axl.core.sync.EdgeSyncRow
import proj.memorchess.axl.core.sync.NodeSyncRow
import proj.memorchess.axl.core.sync.ResolutionSource
import proj.memorchess.axl.core.sync.resolve
import proj.memorchess.axl.core.sync.toDataMove
import proj.memorchess.axl.core.sync.toDataNode
import proj.memorchess.axl.core.sync.toEdgeSyncRow
import proj.memorchess.axl.core.sync.toNodeSyncRow

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
 * @param deviceIdentity Stamped onto every persisted node and edge, and used to order this device's
 *   own writes against its earlier ones. See [DeviceIdentity].
 * @param notifyDirty Called after every local write that queues an outbox entry, so
 *   [proj.memorchess.axl.core.sync.SyncEngine] can schedule a push. Never called from
 *   [applySyncedNode]/[applySyncedMove], whose writes are remote in origin.
 */
class TreeStore(
  private val database: DatabaseQueryManager,
  private val prefetchScope: CoroutineScope,
  private val deviceIdentity: DeviceIdentity,
  private val notifyDirty: () -> Unit = {},
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
    mutex.withLock { tree.upsertEdge(edge, fromDepth) }
    if (isGood != null) {
      persistNode(from)
      persistNode(to)
      // The two persists above each queue their own row's outbox entry transactionally. The edge
      // itself is not a row either of them can attribute a stamp to (it is a field inside each
      // node's persisted move maps), so it is marked separately here, at the edge's own deviceSeq.
      database.markDirty(DirtyKey.EdgeKey(from, to), edge.deviceSeq)
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
      mutex.withLock { tree.upsertEdge(edge, insertion.fromDepth) }
      if (insertion.isGood != null) {
        touched += insertion.from
        touched += insertion.to
        dirtyEdges += DirtyKey.EdgeKey(insertion.from, insertion.to) to edge.deviceSeq
      }
    }
    val nodesToPersist = mutex.withLock { touched.mapNotNull { tree[it]?.toDataNode() } }
    if (nodesToPersist.isNotEmpty()) {
      // insertNodes queues each node's own outbox entry transactionally; only the edges themselves
      // need marking here, same as addMove.
      database.insertNodes(*nodesToPersist.toTypedArray())
      for ((edgeKey, seq) in dirtyEdges) database.markDirty(edgeKey, seq)
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
    mutex.withLock { tree.put(existing.copy(cardState = cardState)) }
    // persistNode below queues the node's own outbox entry transactionally with the row write.
    persistNode(positionKey)
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
    node(from)
    mutex.withLock { tree.removeEdge(from, move) }
    val seq = deviceIdentity.nextDeviceSeq()
    // deleteMove queues the edge's own outbox entry transactionally with the tombstone (SOFT only);
    // persistNode below queues the surviving from node's own entry transactionally with its
    // re-derived hasGoodOutgoing.
    database.deleteMove(from, move, mode, deviceIdentity.originDevice, seq, DateUtil.now())
    persistNode(from)
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
    val seq = deviceIdentity.nextDeviceSeq()
    database.deletePosition(positionKey, mode, deviceIdentity.originDevice, seq, DateUtil.now())
    // Re-persist the origins that lost an outgoing edge so their derived hasGoodOutgoing flag
    // reflects the deletion and cannot go stale.
    for (origin in survivingOrigins) {
      persistNode(origin)
    }
    notifyDirty()
  }

  /** Hard wipes every position and move, both in the cache and on disk. */
  suspend fun eraseAll() {
    database.eraseAll()
    mutex.withLock { tree.clear() }
  }

  /**
   * Applies a node pulled from `/v1/sync`, after resolving it against the local copy via
   * [proj.memorchess.axl.core.sync.resolve]. Returns which side won. On [ResolutionSource.REMOTE]
   * the row is written through [DatabaseQueryManager.applyRemoteNode] (no outbox entry, per its own
   * doc) and the position is evicted from the in memory cache so the next [node] call reloads it.
   * On [ResolutionSource.LOCAL] nothing is written.
   */
  suspend fun applySyncedNode(remote: NodeSyncRow): ResolutionSource {
    val local = database.getPositionIncludingDeleted(PositionKey(remote.positionKey))
    val resolution = resolve(local?.toNodeSyncRow(), remote)
    if (resolution.source == ResolutionSource.REMOTE) {
      val dataNode =
        remote.toDataNode(
          existingMoves = local?.previousAndNextMoves ?: PreviousAndNextMoves(),
          existingDepth = local?.depth ?: 0,
          existingHasGoodOutgoing = local?.hasGoodOutgoing ?: false,
          existingCreatedAt = local?.createdAt ?: remote.updatedAt,
        )
      database.applyRemoteNode(dataNode)
      mutex.withLock { tree.removeNode(dataNode.positionKey) }
    }
    return resolution.source
  }

  /**
   * Applies a move pulled from `/v1/sync`, after resolving it the same way [applySyncedNode] does.
   * On [ResolutionSource.REMOTE] the move is written through
   * [DatabaseQueryManager.applyRemoteMove], both endpoints' derived [DataNode.hasGoodOutgoing] is
   * refreshed if the write changed it (mirrors the concern already documented on [deleteMove]: a
   * good edge appearing or disappearing must not leave the flag stale), and both endpoints are
   * evicted from the cache.
   */
  suspend fun applySyncedMove(remote: EdgeSyncRow): ResolutionSource {
    val originKey = PositionKey(remote.origin)
    val destinationKey = PositionKey(remote.destination)
    val local = localEdgeSyncRow(originKey, remote.move)
    val resolution = resolve(local, remote)
    if (resolution.source == ResolutionSource.REMOTE) {
      database.applyRemoteMove(remote.toDataMove())
      refreshHasGoodOutgoingIfChanged(originKey)
      mutex.withLock {
        tree.removeNode(originKey)
        tree.removeNode(destinationKey)
      }
    }
    return resolution.source
  }

  /**
   * The local counterpart of a pulled edge, as an [EdgeSyncRow], or `null` when unknown locally.
   */
  private suspend fun localEdgeSyncRow(origin: PositionKey, move: String): EdgeSyncRow? =
    database
      .getPositionIncludingDeleted(origin)
      ?.previousAndNextMoves
      ?.nextMoves
      ?.get(move)
      ?.toEdgeSyncRow()

  /**
   * Re-derives [origin]'s [DataNode.hasGoodOutgoing] from its own move maps and re-persists it,
   * without an outbox entry, only when the value actually changed.
   */
  private suspend fun refreshHasGoodOutgoingIfChanged(origin: PositionKey) {
    val node = database.getPositionIncludingDeleted(origin) ?: return
    val recomputed =
      node.previousAndNextMoves.nextMoves.values.any { it.isGood == true && !it.isDeleted }
    if (recomputed != node.hasGoodOutgoing) {
      database.applyRemoteNode(node.copy(hasGoodOutgoing = recomputed))
    }
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
    originDevice = originDevice,
    deviceSeq = deviceSeq,
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

private val LOGGER = Logger.withTag("TreeStore")
