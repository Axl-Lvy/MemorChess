package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.graph.TrainingEntry
import proj.memorchess.axl.core.scheduling.CardPhase

/**
 * Non persistent [DatabaseQueryManager] backing throwaway
 * [proj.memorchess.axl.core.graph.TreeStore] instances that live for a single screen.
 */
class InMemoryDatabaseQueryManager : DatabaseQueryManager {

  /** Soft deleted nodes stay here with their flag set. */
  private val nodes: MutableMap<PositionKey, DataNode> = mutableMapOf()

  /** Keyed by the dirty key itself so a repeat mark collapses. */
  private val outbox: LinkedHashMap<DirtyKey, Long> = linkedMapOf()

  private val repertoires: MutableMap<String, DataRepertoire> = mutableMapOf()

  private val tags: MutableMap<Triple<PositionKey, PositionKey, String>, DataEdgeRepertoireTag> =
    mutableMapOf()

  private val trainable: MutableMap<Pair<PositionKey, String>, Instant?> = mutableMapOf()

  override suspend fun getPosition(positionKey: PositionKey): DataNode? =
    nodes[positionKey]?.takeIf { !it.isDeleted }

  override suspend fun getPositionIncludingDeleted(positionKey: PositionKey): DataNode? =
    nodes[positionKey]

  /** Upserts [node] with the same move merge as [insertNodes], minus the outbox mark. */
  override suspend fun applyRemoteNode(node: DataNode) {
    val existing = nodes[node.positionKey]
    nodes[node.positionKey] =
      if (existing == null) node
      else
        node.copy(
          previousAndNextMoves =
            PreviousAndNextMoves(
              previousMoves =
                existing.previousAndNextMoves.previousMoves +
                  node.previousAndNextMoves.previousMoves,
              nextMoves =
                existing.previousAndNextMoves.nextMoves + node.previousAndNextMoves.nextMoves,
            )
        )
  }

  /** Writes [move] into both endpoints' move maps; a no-op when either endpoint is missing. */
  override suspend fun applyRemoteMove(move: DataMove) {
    val origin = nodes[move.origin] ?: return
    val destination = nodes[move.destination] ?: return
    nodes[move.origin] =
      origin.copy(
        previousAndNextMoves =
          origin.previousAndNextMoves.copy(
            nextMoves = origin.previousAndNextMoves.nextMoves + (move.move to move)
          )
      )
    nodes[move.destination] =
      destination.copy(
        previousAndNextMoves =
          destination.previousAndNextMoves.copy(
            previousMoves = destination.previousAndNextMoves.previousMoves + (move.move to move)
          )
      )
  }

  override suspend fun getNodesPage(cursor: String?, limit: Int): NodesPage {
    require(limit > 0) { "Page limit must be strictly positive, was $limit" }
    val page =
      live()
        .filter { cursor == null || it.positionKey.value > cursor }
        .sortedBy { it.positionKey.value }
        .take(limit)
    val nextCursor = if (page.size == limit) page.last().positionKey.value else null
    return NodesPage(page, nextCursor)
  }

  /**
   * Upserts each node and queues it in the outbox. Move maps are merged, so a stored move absent
   * from the incoming row (such as a tombstone a delete just wrote) survives.
   */
  override suspend fun insertNodes(vararg positions: DataNode) {
    positions.forEach { incoming ->
      val existing = nodes[incoming.positionKey]
      val merged =
        if (existing == null) incoming
        else
          incoming.copy(
            previousAndNextMoves =
              PreviousAndNextMoves(
                previousMoves =
                  existing.previousAndNextMoves.previousMoves +
                    incoming.previousAndNextMoves.previousMoves,
                nextMoves =
                  existing.previousAndNextMoves.nextMoves + incoming.previousAndNextMoves.nextMoves,
              )
          )
      nodes[incoming.positionKey] = merged
      mark(DirtyKey.NodeKey(incoming.positionKey), incoming.deviceSeq)
    }
  }

  override suspend fun deletePosition(
    position: PositionKey,
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ) {
    val node = nodes[position] ?: return
    softDelete(position, node, originDevice, deviceSeq, updatedAt)
  }

  /** Tombstones [node] and the copy every neighbour holds of each incident edge. */
  private fun softDelete(
    position: PositionKey,
    node: DataNode,
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ) {
    if (node.isDeleted) return
    val stampedMoves = tombstoneAll(node.previousAndNextMoves, originDevice, deviceSeq, updatedAt)
    nodes[position] =
      node.copy(
        isDeleted = true,
        updatedAt = updatedAt,
        originDevice = originDevice,
        deviceSeq = deviceSeq,
        previousAndNextMoves = stampedMoves,
      )
    mark(DirtyKey.NodeKey(position), deviceSeq)
    for ((move, edge) in node.previousAndNextMoves.nextMoves) {
      if (edge.isDeleted) continue
      tombstoneInNeighbor(
        edge.destination,
        move,
        isNext = false,
        originDevice,
        deviceSeq,
        updatedAt,
      )
      mark(DirtyKey.EdgeKey(position, edge.destination), deviceSeq)
    }
    for ((move, edge) in node.previousAndNextMoves.previousMoves) {
      if (edge.isDeleted) continue
      tombstoneInNeighbor(edge.origin, move, isNext = true, originDevice, deviceSeq, updatedAt)
      mark(DirtyKey.EdgeKey(edge.origin, position), deviceSeq)
    }
  }

  /** Tombstones every move in [moves], for the deleted node's own denormalized copy. */
  private fun tombstoneAll(
    moves: PreviousAndNextMoves,
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ): PreviousAndNextMoves =
    PreviousAndNextMoves(
      previousMoves =
        moves.previousMoves.values.map { it.tombstoneIfLive(originDevice, deviceSeq, updatedAt) },
      nextMoves =
        moves.nextMoves.values.map { it.tombstoneIfLive(originDevice, deviceSeq, updatedAt) },
    )

  /**
   * Tombstones [move] in [neighborKey]'s `nextMoves` when [isNext], else in its `previousMoves`.
   */
  private fun tombstoneInNeighbor(
    neighborKey: PositionKey,
    move: String,
    isNext: Boolean,
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ) {
    val neighbor = nodes[neighborKey] ?: return
    val moves = neighbor.previousAndNextMoves
    val updated =
      if (isNext) {
        val edge = moves.nextMoves[move] ?: return
        moves.copy(
          nextMoves = moves.nextMoves + (move to edge.tombstone(originDevice, deviceSeq, updatedAt))
        )
      } else {
        val edge = moves.previousMoves[move] ?: return
        moves.copy(
          previousMoves =
            moves.previousMoves + (move to edge.tombstone(originDevice, deviceSeq, updatedAt))
        )
      }
    nodes[neighborKey] = neighbor.copy(previousAndNextMoves = updated)
  }

  /** [tombstone], leaving an already deleted move with its own stamps, as Room and IndexedDB do. */
  private fun DataMove.tombstoneIfLive(
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ): DataMove = if (isDeleted) this else tombstone(originDevice, deviceSeq, updatedAt)

  private fun DataMove.tombstone(
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ): DataMove =
    copy(
      isDeleted = true,
      ownIsGood = null,
      updatedAt = updatedAt,
      originDevice = originDevice,
      deviceSeq = deviceSeq,
    )

  override suspend fun deleteMove(
    origin: PositionKey,
    move: String,
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ) {
    val node = nodes[origin] ?: return
    val edge = node.previousAndNextMoves.nextMoves[move] ?: return
    if (edge.isDeleted) return
    val destination = edge.destination
    nodes[origin] =
      node.copy(
        previousAndNextMoves =
          node.previousAndNextMoves.withTombstonedNext(move, originDevice, deviceSeq, updatedAt)
      )
    val destinationNode = nodes[destination] ?: return
    nodes[destination] =
      destinationNode.copy(
        previousAndNextMoves =
          destinationNode.previousAndNextMoves.withTombstonedPrevious(
            move,
            originDevice,
            deviceSeq,
            updatedAt,
          )
      )
    mark(DirtyKey.EdgeKey(origin, destination), deviceSeq)
  }

  override suspend fun eraseAll() {
    nodes.clear()
    outbox.clear()
    repertoires.clear()
    tags.clear()
    trainable.clear()
  }

  override suspend fun getLastUpdate(): Instant? =
    nodes.values
      .flatMap { node ->
        val moves = node.previousAndNextMoves
        listOf(node.updatedAt) + (moves.previousMoves + moves.nextMoves).values.map { it.updatedAt }
      }
      .maxOrNull()

  /** Non soft deleted rows; a full scan is acceptable only because this store is throwaway. */
  private fun live(): List<DataNode> = nodes.values.filter { !it.isDeleted }

  private fun DataNode.isInSession(): Boolean =
    cardState.phase == CardPhase.LEARNING || cardState.phase == CardPhase.RELEARNING

  private fun DataNode.toTrainingEntry(): TrainingEntry = TrainingEntry(positionKey, cardState)

  override suspend fun nextReadyLearningCard(now: Instant, repertoireId: String?): TrainingEntry? =
    live()
      .filter { it.hasGoodOutgoing && it.isInSession() && it.cardState.dueDate <= now }
      .filter { isTrainableFor(it.positionKey, repertoireId) }
      .minByOrNull { it.cardState.dueDate }
      ?.toTrainingEntry()

  override suspend fun nextPendingLearningCard(
    now: Instant,
    repertoireId: String?,
  ): TrainingEntry? =
    live()
      .filter { it.hasGoodOutgoing && it.isInSession() && it.cardState.dueDate > now }
      .filter { isTrainableFor(it.positionKey, repertoireId) }
      .minByOrNull { it.cardState.dueDate }
      ?.toTrainingEntry()

  override suspend fun nextDueReviewCard(
    dayEndExclusive: Instant,
    repertoireId: String?,
  ): TrainingEntry? =
    live()
      .filter {
        it.hasGoodOutgoing &&
          it.cardState.phase == CardPhase.REVIEW &&
          it.cardState.dueDate < dayEndExclusive
      }
      .filter { isTrainableFor(it.positionKey, repertoireId) }
      .minByOrNull { it.depth }
      ?.toTrainingEntry()

  override suspend fun nextDueNewCard(
    dayEndExclusive: Instant,
    repertoireId: String?,
  ): TrainingEntry? =
    live()
      .filter {
        it.hasGoodOutgoing &&
          it.cardState.phase == CardPhase.NEW &&
          it.cardState.dueDate < dayEndExclusive
      }
      .filter { isTrainableFor(it.positionKey, repertoireId) }
      .minWithOrNull(compareBy({ it.depth }, { it.createdAt }))
      ?.toTrainingEntry()

  override suspend fun getSchedulingCounts(
    dayStart: Instant,
    dayEndExclusive: Instant,
  ): SchedulingCounts {
    val live = live()
    return SchedulingCounts(
      introducedToday =
        live.count {
          it.cardState.firstReview?.let { f -> f >= dayStart && f < dayEndExclusive } == true
        },
      trainedToday =
        live.count {
          it.cardState.lastReview?.let { l -> l >= dayStart && l < dayEndExclusive } == true
        },
      dueReviews =
        live.count {
          it.hasGoodOutgoing &&
            it.cardState.phase == CardPhase.REVIEW &&
            it.cardState.dueDate < dayEndExclusive
        },
      dueNew =
        live.count {
          it.hasGoodOutgoing &&
            it.cardState.phase == CardPhase.NEW &&
            it.cardState.dueDate < dayEndExclusive
        },
      inSession = live.count { it.hasGoodOutgoing && it.isInSession() },
    )
  }

  /**
   * Reference implementation of the convergence rule: a child is counted and descended into only
   * when it has at most one non deleted incoming edge.
   */
  override suspend fun countDescendants(key: PositionKey, cap: Int): Int {
    if (nodes[key]?.takeIf { !it.isDeleted } == null) return 0
    return cappedDescendantCount(key, cap) { liveSingleParentChildren(it) }
  }

  /**
   * Live children of [origin] with at most one live incoming edge, i.e. what a recursive delete
   * removes.
   */
  private fun liveSingleParentChildren(origin: PositionKey): List<PositionKey> {
    val node = nodes[origin]?.takeIf { !it.isDeleted } ?: return emptyList()
    return node.previousAndNextMoves.nextMoves.values
      .filterNot { it.isDeleted }
      .map { it.destination }
      .filter { child ->
        nodes[child]?.takeIf { !it.isDeleted } != null && incomingCount(child) <= 1
      }
  }

  /** Number of non deleted move edges arriving at [destination] across the store. */
  private fun incomingCount(destination: PositionKey): Int =
    nodes.values.sumOf { node ->
      node.previousAndNextMoves.nextMoves.values.count {
        !it.isDeleted && it.destination == destination
      }
    }

  override suspend fun findEligibleAmong(
    keys: List<PositionKey>,
    dayEndExclusive: Instant,
    repertoireId: String?,
  ): TrainingEntry? =
    keys
      .firstNotNullOfOrNull { key ->
        nodes[key]?.takeIf {
          !it.isDeleted &&
            it.hasGoodOutgoing &&
            (it.isInSession() || it.cardState.dueDate < dayEndExclusive) &&
            isTrainableFor(key, repertoireId)
        }
      }
      ?.toTrainingEntry()

  override suspend fun getScopedCounts(
    dayEndExclusive: Instant,
    repertoireId: String,
  ): ScopedSchedulingCounts {
    val scopedLive = live().filter { isTrainableFor(it.positionKey, repertoireId) }
    return ScopedSchedulingCounts(
      dueReviews =
        scopedLive.count {
          it.hasGoodOutgoing &&
            it.cardState.phase == CardPhase.REVIEW &&
            it.cardState.dueDate < dayEndExclusive
        },
      dueNew =
        scopedLive.count {
          it.hasGoodOutgoing &&
            it.cardState.phase == CardPhase.NEW &&
            it.cardState.dueDate < dayEndExclusive
        },
      inSession = scopedLive.count { it.hasGoodOutgoing && it.isInSession() },
    )
  }

  /** `true` when [repertoireId] is `null` (no scope), or [positionKey] is trainable within it. */
  private fun isTrainableFor(positionKey: PositionKey, repertoireId: String?): Boolean =
    repertoireId == null || (positionKey to repertoireId) in trainable

  private fun PreviousAndNextMoves.withTombstonedNext(
    move: String,
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ): PreviousAndNextMoves =
    PreviousAndNextMoves(
      previousMoves.values,
      tombstoneMatching(nextMoves, move, originDevice, deviceSeq, updatedAt),
    )

  private fun PreviousAndNextMoves.withTombstonedPrevious(
    move: String,
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ): PreviousAndNextMoves =
    PreviousAndNextMoves(
      tombstoneMatching(previousMoves, move, originDevice, deviceSeq, updatedAt),
      nextMoves.values,
    )

  private fun tombstoneMatching(
    moves: Map<String, DataMove>,
    move: String,
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ): List<DataMove> =
    moves.values.map {
      if (it.move != move) it else it.tombstone(originDevice, deviceSeq, updatedAt)
    }

  override suspend fun markDirty(key: DirtyKey, deviceSeq: Long) = mark(key, deviceSeq)

  /** Queues [key] at [deviceSeq], keeping the higher sequence on a repeat mark. */
  private fun mark(key: DirtyKey, deviceSeq: Long) {
    val existing = outbox[key]
    if (existing == null || deviceSeq > existing) outbox[key] = deviceSeq
  }

  override suspend fun getOutbox(): List<OutboxEntry> =
    outbox.entries.map { OutboxEntry(it.key, it.value) }.sortedBy { it.deviceSeq }

  override suspend fun clearDirty(entries: Collection<OutboxEntry>) {
    for (entry in entries) {
      val stored = outbox[entry.key] ?: continue
      if (stored <= entry.deviceSeq) outbox.remove(entry.key)
    }
  }

  override suspend fun getRepertoire(id: String): DataRepertoire? =
    repertoires[id]?.takeIf { !it.isDeleted }

  override suspend fun getRepertoireIncludingDeleted(id: String): DataRepertoire? = repertoires[id]

  override suspend fun getRepertoires(): List<DataRepertoire> =
    repertoires.values.filter { !it.isDeleted }

  override suspend fun insertRepertoire(repertoire: DataRepertoire) {
    repertoires[repertoire.id] = repertoire
    mark(DirtyKey.RepertoireKey(repertoire.id), repertoire.deviceSeq)
  }

  override suspend fun applyRemoteRepertoire(repertoire: DataRepertoire) {
    repertoires[repertoire.id] = repertoire
  }

  override suspend fun getTags(
    origin: PositionKey,
    destination: PositionKey,
  ): List<DataEdgeRepertoireTag> =
    tags.values.filter { it.origin == origin && it.destination == destination && !it.isDeleted }

  override suspend fun getTagsTouching(position: PositionKey): List<DataEdgeRepertoireTag> =
    tags.values.filter { (it.origin == position || it.destination == position) && !it.isDeleted }

  override suspend fun getTagIncludingDeleted(
    origin: PositionKey,
    destination: PositionKey,
    repertoireId: String,
  ): DataEdgeRepertoireTag? = tags[Triple(origin, destination, repertoireId)]

  override suspend fun insertTag(tag: DataEdgeRepertoireTag) {
    tags[Triple(tag.origin, tag.destination, tag.repertoireId)] = tag
    mark(DirtyKey.TagKey(tag.origin, tag.destination, tag.repertoireId), tag.deviceSeq)
  }

  override suspend fun applyRemoteTag(tag: DataEdgeRepertoireTag) {
    tags[Triple(tag.origin, tag.destination, tag.repertoireId)] = tag
  }

  override suspend fun edgesTaggedWith(repertoireId: String): List<TaggedEdge> =
    tags.values
      .filter { it.repertoireId == repertoireId && !it.isDeleted }
      .mapNotNull { tag ->
        val move =
          nodes[tag.origin]?.previousAndNextMoves?.nextMoves?.values?.firstOrNull {
            it.destination == tag.destination && !it.isDeleted
          }
        move?.let { TaggedEdge(tag.origin, tag.destination, it.move) }
      }

  override suspend fun replaceTrainableRepertoires(
    positionKey: PositionKey,
    repertoireIds: Set<String>,
    lastReview: Instant?,
  ) {
    trainable.keys.removeAll { it.first == positionKey }
    for (id in repertoireIds) trainable[positionKey to id] = lastReview
  }

  override suspend fun getRepertoireMasterySnapshots(
    repertoireIds: List<String>
  ): Map<String, RepertoireMasterySnapshot> = repertoireIds.associateWith { id ->
    // Correctness backstop, not an optimization: a deleted position's trainable row stays stale
    // until its own delete path clears it. Mirrors the other backends' inner join on the node
    // table.
    val live =
      trainable.entries.filter { it.key.second == id && nodes[it.key.first]?.isDeleted == false }
    val solid = live.count { nodes[it.key.first]?.cardState?.phase == CardPhase.REVIEW }
    RepertoireMasterySnapshot(
      solidCount = solid,
      totalCount = live.size,
      lastReview = live.mapNotNull { it.value }.maxOrNull(),
    )
  }
}
