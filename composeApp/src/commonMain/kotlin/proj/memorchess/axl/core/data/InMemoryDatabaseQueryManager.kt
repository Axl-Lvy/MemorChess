package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.graph.DeleteMode
import proj.memorchess.axl.core.graph.PreviousAndNextMoves
import proj.memorchess.axl.core.graph.TrainingEntry
import proj.memorchess.axl.core.scheduling.CardPhase

/**
 * In memory [DatabaseQueryManager] with no persistence at all.
 *
 * It backs throwaway [proj.memorchess.axl.core.graph.TreeStore] instances that exist only for the
 * lifetime of a single screen, such as the read only repertoire viewer which rebuilds an isolated
 * opening graph from a downloaded PGN every time it opens. Nothing here touches Room or IndexedDB,
 * so it works identically on every target and leaves the user's real graph untouched.
 *
 * Behaviour mirrors the platform implementations closely enough for [TreeStore]: hard deletes
 * physically remove rows and any incident move, soft deletes flip the [DataNode.isDeleted] flag and
 * cascade the tombstone to every incident move, exactly like Room's `softDeleteNode` +
 * `softDeleteMoveFrom` + `softDeleteMoveTo` and IndexedDB's per-store writes. [insertNodes] merges
 * into each row's existing move maps rather than replacing them outright, so a tombstone written by
 * a delete call is never clobbered by a node persist that runs moments later without that edge in
 * its own cache derived payload (the same reason a Room `INSERT ... REPLACE` on the node row leaves
 * unrelated `MoveEntity` rows untouched, and an IndexedDB `put` only touches the row it names).
 */
class InMemoryDatabaseQueryManager : DatabaseQueryManager {

  /** Backing store, keyed by position. Soft-deleted nodes stay here with their flag set. */
  private val nodes: MutableMap<PositionKey, DataNode> = mutableMapOf()

  /** Backing outbox, keyed by the dirty key itself so a repeat mark is a no-op collapse. */
  private val outbox: LinkedHashMap<DirtyKey, Long> = linkedMapOf()

  /** Backing store for the repertoire registry, keyed by id. */
  private val repertoires: MutableMap<String, DataRepertoire> = mutableMapOf()

  /** Backing store for edge to repertoire tags, keyed by the edge's endpoints and repertoire. */
  private val tags: MutableMap<Triple<PositionKey, PositionKey, String>, DataEdgeRepertoireTag> =
    mutableMapOf()

  override suspend fun getPosition(positionKey: PositionKey): DataNode? =
    nodes[positionKey]?.takeIf { !it.isDeleted }

  override suspend fun getPositionIncludingDeleted(positionKey: PositionKey): DataNode? =
    nodes[positionKey]

  /** Mirrors [insertNodes]' move-merge trick for one node, minus the outbox mark. */
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

  /**
   * Writes [move] into both endpoints' denormalized move maps, mirroring how this backend already
   * denormalizes every other move write. A no-op when either endpoint does not exist yet.
   */
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
    // Sorting and slicing the backing map is acceptable here precisely because this is the
    // throwaway
    // in memory store, not the disk backed path: the Room and IndexedDB backends express the same
    // ordered, cursor bounded slice as a single bounded query.
    val page =
      live()
        .filter { cursor == null || it.positionKey.value > cursor }
        .sortedBy { it.positionKey.value }
        .take(limit)
    val nextCursor = if (page.size == limit) page.last().positionKey.value else null
    return NodesPage(page, nextCursor)
  }

  /**
   * Replaces each node's scalar fields but merges its [DataNode.previousAndNextMoves]: an incoming
   * move overrides the stored one with the same key, but a move present only in the stored row (for
   * example a tombstone a delete call just wrote) survives. This mirrors the per-row upsert every
   * other backend gets for free from a normalized move table. Also queues each node for the next
   * sync push at its own [DataNode.deviceSeq], in the same call as the row write it names.
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
    mode: DeleteMode,
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ) {
    val node = nodes[position] ?: return
    when (mode) {
      DeleteMode.HARD -> hardDelete(position)
      DeleteMode.SOFT -> softDelete(position, node, originDevice, deviceSeq, updatedAt)
    }
  }

  /** Physically removes [position] and drops any move that pointed to or came from it. */
  private fun hardDelete(position: PositionKey) {
    nodes.remove(position)
    for ((key, other) in nodes.toMap()) {
      val moves = other.previousAndNextMoves
      val previousMoves = moves.previousMoves.values.filter { it.origin != position }
      val nextMoves = moves.nextMoves.values.filter { it.destination != position }
      if (
        previousMoves.size != moves.previousMoves.size || nextMoves.size != moves.nextMoves.size
      ) {
        nodes[key] =
          other.copy(previousAndNextMoves = PreviousAndNextMoves(previousMoves, nextMoves))
      }
    }
  }

  /**
   * Flips [node]'s [DataNode.isDeleted] flag and cascades the tombstone to the denormalized copy
   * every neighbour holds of the same edge, exactly like Room's softDeleteMoveFrom/softDeleteMoveTo
   * and IndexedDB's per-store writes.
   */
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
        moves.previousMoves.values.map { it.tombstone(originDevice, deviceSeq, updatedAt) },
      nextMoves = moves.nextMoves.values.map { it.tombstone(originDevice, deviceSeq, updatedAt) },
    )

  /**
   * Flips the matching move to deleted inside [neighborKey]'s own denormalized copy. [isNext] is
   * `true` when the edge lives in the neighbour's `nextMoves` (the neighbour is the edge's origin),
   * `false` when it lives in its `previousMoves` (the neighbour is the edge's destination).
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

  private fun DataMove.tombstone(
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ): DataMove =
    copy(
      isDeleted = true,
      updatedAt = updatedAt,
      originDevice = originDevice,
      deviceSeq = deviceSeq,
    )

  override suspend fun deleteMove(
    origin: PositionKey,
    move: String,
    mode: DeleteMode,
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ) {
    val node = nodes[origin] ?: return
    val edge = node.previousAndNextMoves.nextMoves[move] ?: return
    if (mode == DeleteMode.SOFT && edge.isDeleted) return
    val destination = edge.destination
    nodes[origin] =
      node.copy(
        previousAndNextMoves =
          node.previousAndNextMoves.withoutNext(move, mode, originDevice, deviceSeq, updatedAt)
      )
    val destinationNode = nodes[destination] ?: return
    nodes[destination] =
      destinationNode.copy(
        previousAndNextMoves =
          destinationNode.previousAndNextMoves.withoutPrevious(
            move,
            mode,
            originDevice,
            deviceSeq,
            updatedAt,
          )
      )
    if (mode == DeleteMode.SOFT) mark(DirtyKey.EdgeKey(origin, destination), deviceSeq)
  }

  override suspend fun eraseAll() {
    nodes.clear()
    outbox.clear()
    repertoires.clear()
    tags.clear()
  }

  override suspend fun getLastUpdate(): Instant? =
    nodes.values
      .flatMap { node ->
        val moves = node.previousAndNextMoves
        listOf(node.updatedAt) + (moves.previousMoves + moves.nextMoves).values.map { it.updatedAt }
      }
      .maxOrNull()

  /**
   * Live (non soft deleted) rows. Iterating the backing map is acceptable here precisely because
   * this is the throwaway in memory store, not the disk backed path: the same predicates are
   * expressed as bounded indexed queries on the Room and IndexedDB backends.
   */
  private fun live(): List<DataNode> = nodes.values.filter { !it.isDeleted }

  private fun DataNode.isInSession(): Boolean =
    cardState.phase == CardPhase.LEARNING || cardState.phase == CardPhase.RELEARNING

  private fun DataNode.toTrainingEntry(): TrainingEntry = TrainingEntry(positionKey, cardState)

  override suspend fun nextReadyLearningCard(now: Instant): TrainingEntry? =
    live()
      .filter { it.hasGoodOutgoing && it.isInSession() && it.cardState.dueDate <= now }
      .minByOrNull { it.cardState.dueDate }
      ?.toTrainingEntry()

  override suspend fun nextPendingLearningCard(now: Instant): TrainingEntry? =
    live()
      .filter { it.hasGoodOutgoing && it.isInSession() && it.cardState.dueDate > now }
      .minByOrNull { it.cardState.dueDate }
      ?.toTrainingEntry()

  override suspend fun nextDueReviewCard(dayEndExclusive: Instant): TrainingEntry? =
    live()
      .filter {
        it.hasGoodOutgoing &&
          it.cardState.phase == CardPhase.REVIEW &&
          it.cardState.dueDate < dayEndExclusive
      }
      .minByOrNull { it.depth }
      ?.toTrainingEntry()

  override suspend fun nextDueNewCard(dayEndExclusive: Instant): TrainingEntry? =
    live()
      .filter {
        it.hasGoodOutgoing &&
          it.cardState.phase == CardPhase.NEW &&
          it.cardState.dueDate < dayEndExclusive
      }
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
   * Capped breadth first descendant count over the in memory map. This is the cross backend
   * reference implementation of the convergence rule: a child is counted and descended into only
   * when its total non deleted incoming edge count is at most one, so a position reachable through
   * an outside parent is left alone. Iterating the backing map is acceptable here because this is
   * the throwaway in memory store; the Room and IndexedDB backends express the same walk with point
   * queries.
   */
  override suspend fun countDescendants(key: PositionKey, cap: Int): Int {
    if (nodes[key]?.takeIf { !it.isDeleted } == null) return 0
    return cappedDescendantCount(key, cap) { liveSingleParentChildren(it) }
  }

  /**
   * Non-deleted children of [origin] whose only non-deleted incoming edge comes from within the
   * subtree (incoming count at most one), i.e. the positions a recursive delete would remove. A
   * convergent position reachable through an outside parent is excluded.
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
  ): TrainingEntry? =
    keys
      .firstNotNullOfOrNull { key ->
        nodes[key]?.takeIf {
          !it.isDeleted &&
            it.hasGoodOutgoing &&
            (it.isInSession() || it.cardState.dueDate < dayEndExclusive)
        }
      }
      ?.toTrainingEntry()

  private fun PreviousAndNextMoves.withoutNext(
    move: String,
    mode: DeleteMode,
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ): PreviousAndNextMoves =
    PreviousAndNextMoves(
      previousMoves.values,
      removeOrFlag(nextMoves, move, mode, originDevice, deviceSeq, updatedAt),
    )

  private fun PreviousAndNextMoves.withoutPrevious(
    move: String,
    mode: DeleteMode,
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ): PreviousAndNextMoves =
    PreviousAndNextMoves(
      removeOrFlag(previousMoves, move, mode, originDevice, deviceSeq, updatedAt),
      nextMoves.values,
    )

  private fun removeOrFlag(
    moves: Map<String, DataMove>,
    move: String,
    mode: DeleteMode,
    originDevice: String,
    deviceSeq: Long,
    updatedAt: Instant,
  ): List<DataMove> =
    moves.values.mapNotNull {
      if (it.move != move) it
      else
        when (mode) {
          DeleteMode.HARD -> null
          DeleteMode.SOFT -> it.tombstone(originDevice, deviceSeq, updatedAt)
        }
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
}
