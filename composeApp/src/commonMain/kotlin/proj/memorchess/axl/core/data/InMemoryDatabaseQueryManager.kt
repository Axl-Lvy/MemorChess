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
 * physically remove rows and any incident move, soft deletes flip the [DataNode.isDeleted] flag.
 */
class InMemoryDatabaseQueryManager : DatabaseQueryManager {

  /** Backing store, keyed by position. Soft-deleted nodes stay here with their flag set. */
  private val nodes: MutableMap<PositionKey, DataNode> = mutableMapOf()

  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<DataNode> =
    nodes.values.filter { withDeletedOnes || !it.isDeleted }.toList()

  override suspend fun getPosition(positionKey: PositionKey): DataNode? =
    nodes[positionKey]?.takeIf { !it.isDeleted }

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

  override suspend fun insertNodes(vararg positions: DataNode) {
    positions.forEach { nodes[it.positionKey] = it }
  }

  override suspend fun deletePosition(position: PositionKey, mode: DeleteMode) {
    val node = nodes[position] ?: return
    when (mode) {
      DeleteMode.HARD -> {
        nodes.remove(position)
        // Drop any move that pointed to or came from the removed position.
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
      DeleteMode.SOFT -> nodes[position] = node.copy(isDeleted = true)
    }
  }

  override suspend fun deleteMove(origin: PositionKey, move: String, mode: DeleteMode) {
    val node = nodes[origin] ?: return
    val edge = node.previousAndNextMoves.nextMoves[move] ?: return
    val destination = edge.destination
    nodes[origin] =
      node.copy(previousAndNextMoves = node.previousAndNextMoves.withoutNext(move, mode))
    val destinationNode = nodes[destination] ?: return
    nodes[destination] =
      destinationNode.copy(
        previousAndNextMoves = destinationNode.previousAndNextMoves.withoutPrevious(move, mode)
      )
  }

  override suspend fun eraseAll() {
    nodes.clear()
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
  ): PreviousAndNextMoves =
    PreviousAndNextMoves(previousMoves.values, removeOrFlag(nextMoves, move, mode))

  private fun PreviousAndNextMoves.withoutPrevious(
    move: String,
    mode: DeleteMode,
  ): PreviousAndNextMoves =
    PreviousAndNextMoves(removeOrFlag(previousMoves, move, mode), nextMoves.values)

  private fun removeOrFlag(
    moves: Map<String, DataMove>,
    move: String,
    mode: DeleteMode,
  ): List<DataMove> =
    moves.values.mapNotNull {
      if (it.move != move) it
      else
        when (mode) {
          DeleteMode.HARD -> null
          DeleteMode.SOFT -> it.copy(isDeleted = true)
        }
    }
}
