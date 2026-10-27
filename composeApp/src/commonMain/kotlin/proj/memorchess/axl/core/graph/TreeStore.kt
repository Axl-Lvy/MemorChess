package proj.memorchess.axl.core.graph

import co.touchlab.kermit.Logger
import proj.memorchess.axl.core.data.DESCENDANT_COUNT_CAP
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.DirtyKey
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.data.TaggedEdge
import proj.memorchess.axl.core.data.repertoire.RepertoireColor
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.scheduling.CardState
import proj.memorchess.axl.core.sync.DeviceIdentity
import proj.memorchess.axl.core.sync.toDataMove
import proj.memorchess.axl.core.sync.toDataNode

/**
 * Single chokepoint for local mutation of the opening tree, writing through a bounded [NodeCache]
 * to authoritative persistence. Unclassified moves (`isGood == null`) live in the cache only.
 *
 * Holds no lock of its own: [NodeCache] owns the mutex, and a mutation survives an in flight load
 * of the same key (see [NodeCache.resolve]).
 *
 * @param notifyDirty Called after every local write that queues an outbox entry.
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

  private val cleanup = HolderCleanup(database)

  /**
   * Resolves the node at [positionKey] through the cache, prefetching its neighbours on a miss.
   * `null` when the position is neither persisted nor a resident exploration only node.
   */
  suspend fun node(positionKey: PositionKey): Node? {
    val (node, hit) = cache.resolve(positionKey)
    if (!hit && node != null) prefetcher.warmNeighbors(node)
    return node
  }

  /**
   * The [NodeState] of [positionKey] reached from [arrivedFrom], or [NodeState.UNKNOWN] when it
   * cannot be resolved.
   */
  suspend fun computeState(positionKey: PositionKey, arrivedFrom: PositionKey?): NodeState =
    node(positionKey)?.computeState(arrivedFrom) ?: NodeState.UNKNOWN

  /** Depth of [positionKey], or [Int.MAX_VALUE] when it cannot be resolved. */
  suspend fun getDepth(positionKey: PositionKey): Int = node(positionKey)?.depth ?: Int.MAX_VALUE

  /**
   * Counts the non deleted positions a recursive delete from [key] would remove, [key] included,
   * capped at [cap].
   */
  suspend fun countDescendants(key: PositionKey, cap: Int = DESCENDANT_COUNT_CAP): Int =
    database.countDescendants(key, cap)

  /** Ensures [positionKey] is cached at [depth] without persisting it. */
  suspend fun ensurePosition(positionKey: PositionKey, depth: Int) {
    cache.ensure(positionKey, depth)
  }

  /**
   * Adds or replaces an edge, persisting it as a manual hold unless [isGood] is `null`. An existing
   * edge keeps its [Edge.createdAt], so replaying a line never reshuffles new card order.
   *
   * @param move SAN of the move.
   * @param fromDepth Depth of [from], used when the node has to be inserted.
   */
  suspend fun addMove(
    from: PositionKey,
    move: String,
    to: PositionKey,
    isGood: Boolean?,
    fromDepth: Int,
  ): Edge {
    val createdAt = node(from)?.outgoing?.get(move)?.createdAt ?: DateUtil.now()
    // TODO: this allocates a deviceSeq even for an exploration only move that is never persisted.
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
        ownIsGood = isGood,
      )
    cache.upsertEdge(edge, fromDepth)
    if (isGood != null) {
      persistNode(from)
      persistNode(to)
      // The node persists queue their own outbox entries; the edge is not a row, so mark it here.
      database.markDirty(DirtyKey.EdgeKey(from, to), edge.deviceSeq)
      // Only from's outgoing edges changed, so to's trainable rows are unaffected.
      trainable.recompute(from)
      notifyDirty()
    }
    return edge
  }

  /**
   * Like calling [addMove] for each of [moves] in order, but persists every touched node exactly
   * once in a single [DatabaseQueryManager.insertNodes] call.
   */
  suspend fun addMoves(moves: List<MoveInsertion>) {
    if (moves.isEmpty()) return
    val now = DateUtil.now()
    val touched = linkedSetOf<PositionKey>()
    val dirtyEdges = mutableListOf<Pair<DirtyKey.EdgeKey, Long>>()
    // TODO: this allocates a deviceSeq even for exploration only moves that are never persisted.
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
          ownIsGood = insertion.isGood,
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
      // insertNodes queues the nodes' outbox entries; edges are not rows, so mark them here.
      database.insertNodes(*nodesToPersist.toTypedArray())
      for ((edgeKey, seq) in dirtyEdges) database.markDirty(edgeKey, seq)
      for (origin in touched) trainable.recompute(origin)
      notifyDirty()
    }
  }

  /**
   * Installs [moves] as holds of [repertoireId] at their effective marking, creating no manual
   * hold, and replaces the repertoire's earlier holds as [uninstall] does. Persists the touched
   * positions but queues no move for push: only tags and positions sync.
   */
  suspend fun install(repertoireId: String, moves: List<InstalledMove>) {
    if (moves.isEmpty()) return
    val dropped = droppedBy(repertoireId, moves.map { TaggedEdge(it.from, it.to, it.move) })
    for (move in moves) {
      tagStore.tagInstalled(move.from, move.to, repertoireId, move.move, move.isGood)
    }
    val now = DateUtil.now()
    val touched = linkedSetOf<PositionKey>()
    for (move in moves) {
      val prior = priorManualHold(move.from, move.move)
      val effective =
        effectiveMarking(
          prior?.ownIsGood,
          database.getTags(move.from, move.to).mapNotNull { it.isGood },
        ) ?: continue
      val edge =
        Edge(
          from = move.from,
          move = move.move,
          to = move.to,
          isGood = effective,
          createdAt = prior?.createdAt ?: now,
          updatedAt = prior?.updatedAt ?: now,
          originDevice = prior?.originDevice.orEmpty(),
          deviceSeq = prior?.deviceSeq ?: 0L,
          ownIsGood = prior?.ownIsGood,
        )
      cache.upsertEdge(edge, move.fromDepth)
      touched += move.from
      touched += move.to
    }
    val nodesToPersist = touched.mapNotNull { cache.peek(it)?.toDataNode() }
    database.insertNodes(*nodesToPersist.toTypedArray())
    for (origin in touched) trainable.recompute(origin)
    notifyDirty()
    releaseHolds(repertoireId, dropped)
  }

  /** Number of moves an [install] of [edges] as [repertoireId] would delete. */
  suspend fun previewInstall(repertoireId: String, edges: List<TaggedEdge>): Int {
    val dropped = droppedBy(repertoireId, edges)
    if (dropped.isEmpty()) return 0
    return cleanup
      .plan(dropped, HoldOverlay(repertoireId, dropped.toSet(), edges.toSet()))
      .moves
      .size
  }

  /**
   * The edges [repertoireId] holds that [edges] no longer contains. Membership tags the user added
   * by saving while scoped to the repertoire are not holds, so they stay.
   */
  private suspend fun droppedBy(repertoireId: String, edges: List<TaggedEdge>): List<TaggedEdge> {
    val kept = edges.map { it.origin to it.destination }.toSet()
    return tagStore.edgesTaggedWith(repertoireId).filter {
      (it.origin to it.destination) !in kept &&
        tagStore.markings(it.origin, it.destination)[repertoireId] != null
    }
  }

  /** The persisted move [move] from [from], live or tombstoned, whose stamps an install keeps. */
  private suspend fun priorManualHold(from: PositionKey, move: String): Edge? {
    val live = node(from)?.outgoing?.get(move)
    if (live != null && live.isGood != null) return live
    val stored =
      database.getPositionIncludingDeleted(from)?.previousAndNextMoves?.nextMoves?.get(move)
        ?: return null
    return Edge(
      from = stored.origin,
      move = stored.move,
      to = stored.destination,
      isGood = stored.isGood,
      createdAt = stored.createdAt,
      updatedAt = stored.updatedAt,
      isDeleted = stored.isDeleted,
      originDevice = stored.originDevice,
      deviceSeq = stored.deviceSeq,
      ownIsGood = stored.ownIsGood,
    )
  }

  /**
   * Gives every move of [edges] the user does not hold yet a manual hold at its effective marking.
   */
  suspend fun holdManually(edges: List<TaggedEdge>) {
    for (tagged in edges) {
      val origin = node(tagged.origin) ?: continue
      val edge = origin.outgoing[tagged.move] ?: continue
      val marking = edge.isGood ?: continue
      if (edge.ownIsGood != null) continue
      addMove(edge.from, edge.move, edge.to, marking, origin.depth)
    }
  }

  /**
   * Forks [sourceId] into [newId], holding every source move manually so the fork survives the
   * source's uninstall.
   *
   * @throws IllegalArgumentException if [newId] is blank or contains a comma.
   */
  suspend fun fork(sourceId: String, newId: String, newName: String, color: RepertoireColor?) {
    holdManually(tagStore.edgesTaggedWith(sourceId))
    tagStore.fork(sourceId, newId, newName, color)
  }

  /** Number of moves [uninstall] of [repertoireId] would delete. */
  suspend fun previewUninstall(repertoireId: String): Int {
    val edges = tagStore.edgesTaggedWith(repertoireId)
    return cleanup.plan(edges, HoldOverlay(repertoireId, edges.toSet(), emptySet())).moves.size
  }

  /**
   * Uninstalls [repertoireId]: tombstones its registry row and tags, then deletes every move no
   * holder keeps and every position the deletion cuts off, with their review progress. Moves the
   * user holds or another installed repertoire holds stay, with their re-derived marking.
   *
   * @throws IllegalArgumentException if [repertoireId] is not an installed repertoire.
   */
  suspend fun uninstall(repertoireId: String) {
    val registered = tagStore.repertoire(repertoireId)
    require(registered?.installedVersion != null) { "$repertoireId is not an installed repertoire" }
    // The registry row goes last, so an interrupted uninstall stays installed and can be retried.
    releaseHolds(repertoireId, tagStore.edgesTaggedWith(repertoireId))
    tagStore.unregister(registered)
  }

  /**
   * Removes [repertoireId]'s holds on [edges], re-derives the marking of the moves something still
   * holds, and applies the [HolderCleanup] plan. An unheld move is deleted without an outbox entry,
   * since every device derives move existence from the holders; a removed manual hold, a tag and a
   * deleted position are queued for push.
   */
  private suspend fun releaseHolds(repertoireId: String, edges: List<TaggedEdge>) {
    if (edges.isEmpty()) return
    for (edge in edges) tagStore.untag(edge.origin, edge.destination, repertoireId)
    val plan = cleanup.plan(edges)
    val deleted = plan.moves.map { TaggedEdge(it.origin, it.destination, it.move) }.toSet()
    for (move in plan.remarked) database.applyRemoteMove(move)
    val now = DateUtil.now()
    for (move in plan.moves) {
      tagStore.tombstoneTags(move.origin, move.destination)
      if (move.ownIsGood != null) {
        database.deleteMove(
          move.origin,
          move.move,
          deviceIdentity.originDevice,
          deviceIdentity.nextDeviceSeq(),
          now,
        )
      } else {
        database.applyRemoteMove(move.copy(isDeleted = true))
      }
    }
    for (position in plan.positions) {
      database.deletePosition(
        position,
        deviceIdentity.originDevice,
        deviceIdentity.nextDeviceSeq(),
        now,
      )
      trainable.clear(position)
    }
    val touched = linkedSetOf<PositionKey>()
    for (edge in edges + deleted) {
      touched += edge.origin
      touched += edge.destination
    }
    for (position in touched) cache.invalidate(position)
    val survivingOrigins =
      (edges.map { it.origin } + deleted.map { it.origin }).toSet() - plan.positions.toSet()
    for (origin in survivingOrigins) {
      refreshHasGoodOutgoing(origin)
      trainable.recompute(origin)
    }
    notifyDirty()
  }

  /**
   * Re-derives the stored [DataNode.hasGoodOutgoing] of the live [origin], without outbox entry.
   */
  private suspend fun refreshHasGoodOutgoing(origin: PositionKey) {
    val node = database.getPosition(origin) ?: return
    val recomputed =
      node.previousAndNextMoves.nextMoves.values.any { it.isGood == true && !it.isDeleted }
    if (recomputed != node.hasGoodOutgoing) {
      database.applyRemoteNode(node.copy(hasGoodOutgoing = recomputed))
    }
  }

  /**
   * Persists a graph from [GraphSerializer.deserialize] as manual holds, restamped with this
   * device's identity. Each edge gets one sequence shared by both endpoint copies, and `updatedAt`
   * is kept as recorded so a restore still loses to a newer edit.
   */
  suspend fun importNodes(nodes: List<DataNode>) {
    if (nodes.isEmpty()) return

    val edgeStamps = mutableMapOf<EdgeIdentity, DataMove>()
    fun stamp(move: DataMove): DataMove =
      edgeStamps.getOrPut(EdgeIdentity(move.origin, move.destination, move.move)) {
        move.copy(
          ownIsGood = if (move.isDeleted) null else move.isGood,
          originDevice = deviceIdentity.originDevice,
          deviceSeq = deviceIdentity.nextDeviceSeq(),
        )
      }

    val stampedNodes = nodes.map { importedNode ->
      importedNode.copy(
        previousAndNextMoves =
          PreviousAndNextMoves(
            previousMoves = importedNode.previousAndNextMoves.previousMoves.values.map(::stamp),
            nextMoves = importedNode.previousAndNextMoves.nextMoves.values.map(::stamp),
          ),
        originDevice = deviceIdentity.originDevice,
        deviceSeq = deviceIdentity.nextDeviceSeq(),
      )
    }

    database.insertNodes(*stampedNodes.toTypedArray())
    for (edge in edgeStamps.values) {
      database.markDirty(DirtyKey.EdgeKey(edge.origin, edge.destination), edge.deviceSeq)
    }
    // Invalidate after the durable write, or a retry would read the superseded row.
    for (node in stampedNodes) cache.invalidate(node.positionKey)
    notifyDirty()
  }

  /**
   * Stores [cardState] on the node at [positionKey] and persists it, skipping with a warning when
   * the position no longer resolves.
   */
  suspend fun updateCardState(positionKey: PositionKey, cardState: CardState) {
    val existing = node(positionKey)
    if (existing == null) {
      LOGGER.w { "Skipping card state update for unknown position $positionKey" }
      return
    }
    cache.put(existing.copy(cardState = cardState))
    persistNode(positionKey)
    // Trainable rows carry lastReview, so they change even though the edges did not.
    trainable.recompute(positionKey)
    notifyDirty()
  }

  /**
   * Deletes the move [move] leaving [from] from the cache and the database, re-persisting [from] so
   * its [DataNode.hasGoodOutgoing] stays accurate.
   */
  suspend fun deleteMove(from: PositionKey, move: String) {
    // Resolve through node() so from is resident: persistNode(from) is a no-op on a cache miss.
    val destination = node(from)?.outgoing?.get(move)?.to
    cache.removeEdge(from, move, destination)
    val seq = deviceIdentity.nextDeviceSeq()
    database.deleteMove(from, move, deviceIdentity.originDevice, seq, DateUtil.now())
    persistNode(from)
    // Tombstone before recomputing so the deleted edge's tags are excluded.
    if (destination != null) tagStore.tombstoneTags(from, destination)
    trainable.recompute(from)
    notifyDirty()
  }

  /**
   * Deletes the node at [positionKey] and every incident edge from the cache and the database. The
   * database write is authoritative; cache patches are best effort for resident neighbours.
   */
  suspend fun deleteNode(positionKey: PositionKey) {
    val node = node(positionKey)
    val survivingOrigins = mutableSetOf<PositionKey>()
    if (node != null) {
      for (edge in node.outgoing.values) tagStore.tombstoneTags(positionKey, edge.to)
      for (edge in node.incoming.values) tagStore.tombstoneTags(edge.from, positionKey)
    }
    val seq = deviceIdentity.nextDeviceSeq()
    // Write before patching the cache, or a retry could read the superseded row.
    database.deletePosition(positionKey, deviceIdentity.originDevice, seq, DateUtil.now())
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
    // Refresh hasGoodOutgoing on origins that lost an edge.
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

  /** Persists the cached node at [positionKey]; a no-op when it is not cached. */
  private suspend fun persistNode(positionKey: PositionKey) {
    val node = cache.peek(positionKey) ?: return
    database.insertNodes(node.toDataNode())
  }

  /** Builds the [DataNode] to persist, stamping a fresh device sequence on every call. */
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
 * One move to insert through [TreeStore.addMoves], mirroring [TreeStore.addMove]'s parameters.
 *
 * @property isGood Classification of the move; `null` means exploration only.
 */
data class MoveInsertion(
  val from: PositionKey,
  val move: String,
  val to: PositionKey,
  val isGood: Boolean?,
  val fromDepth: Int,
)

/**
 * One move to write through [TreeStore.install].
 *
 * @property isGood The author's marking.
 */
data class InstalledMove(
  val from: PositionKey,
  val move: String,
  val to: PositionKey,
  val isGood: Boolean,
  val fromDepth: Int,
)

/** Identity of one edge, regardless of which endpoint's move map it was read from. */
private data class EdgeIdentity(
  val origin: PositionKey,
  val destination: PositionKey,
  val move: String,
)

private fun Edge.toDataMove(): DataMove =
  DataMove(
    origin = from,
    destination = to,
    move = move,
    isGood = isGood,
    ownIsGood = ownIsGood,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    originDevice = originDevice,
    deviceSeq = deviceSeq,
  )

private val LOGGER = Logger.withTag("TreeStore")
