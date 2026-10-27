package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.TrainingEntry

/**
 * Low level persistence seam for the opening tree. Application code goes through
 * [proj.memorchess.axl.core.graph.TreeStore] instead.
 */
interface DatabaseQueryManager {

  /** Retrieves a specific position, or `null` when missing or soft deleted. */
  suspend fun getPosition(positionKey: PositionKey): DataNode?

  /**
   * Reads up to [limit] live nodes with their edges, ordered by position key, after [cursor]
   * (exclusive), or from the start when [cursor] is `null`.
   *
   * @throws IllegalArgumentException when [limit] is not strictly positive.
   */
  suspend fun getNodesPage(cursor: String?, limit: Int): NodesPage

  /** Soft deletes [position] and its incident moves, leaving tombstones for sync. */
  suspend fun deletePosition(
    position: PositionKey,
    originDevice: String = "",
    deviceSeq: Long = 0L,
    updatedAt: Instant = DateUtil.now(),
  )

  /** Soft deletes the SAN [move] from [origin], leaving a tombstone for sync. */
  suspend fun deleteMove(
    origin: PositionKey,
    move: String,
    originDevice: String = "",
    deviceSeq: Long = 0L,
    updatedAt: Instant = DateUtil.now(),
  )

  /** Hard wipe of every node, move, repertoire, and edge to repertoire tag. */
  suspend fun eraseAll()

  /** Inserts new positions. */
  suspend fun insertNodes(vararg positions: DataNode)

  /** Retrieves the latest `updatedAt` across nodes and moves. */
  suspend fun getLastUpdate(): Instant?

  /**
   * The trainable learning or relearning card due at or before [now] (inclusive) with the earliest
   * due date, or `null`.
   *
   * @param repertoireId Narrows to positions trainable within this repertoire; `null` means all.
   */
  suspend fun nextReadyLearningCard(now: Instant, repertoireId: String? = null): TrainingEntry?

  /**
   * The trainable learning or relearning card due strictly after [now] with the earliest due date,
   * or `null`. [repertoireId] narrows as in [nextReadyLearningCard].
   */
  suspend fun nextPendingLearningCard(now: Instant, repertoireId: String? = null): TrainingEntry?

  /**
   * The shallowest trainable review card due before [dayEndExclusive], or `null`. [repertoireId]
   * narrows as in [nextReadyLearningCard].
   */
  suspend fun nextDueReviewCard(
    dayEndExclusive: Instant,
    repertoireId: String? = null,
  ): TrainingEntry?

  /**
   * The shallowest, then oldest, trainable new card due before [dayEndExclusive], or `null`.
   * [repertoireId] narrows as in [nextReadyLearningCard].
   */
  suspend fun nextDueNewCard(dayEndExclusive: Instant, repertoireId: String? = null): TrainingEntry?

  /** Counts the [SchedulingCounts] of the day from [dayStart] to [dayEndExclusive] (exclusive). */
  suspend fun getSchedulingCounts(dayStart: Instant, dayEndExclusive: Instant): SchedulingCounts

  /**
   * The first of [keys] that is trainable and either mid learning or due before [dayEndExclusive],
   * or `null`. [repertoireId] narrows as in [nextReadyLearningCard].
   */
  suspend fun findEligibleAmong(
    keys: List<PositionKey>,
    dayEndExclusive: Instant,
    repertoireId: String? = null,
  ): TrainingEntry?

  /**
   * The [SchedulingCounts] fields narrowed to [repertoireId]. The daily caps have no scoped
   * equivalent and stay in [getSchedulingCounts].
   */
  suspend fun getScopedCounts(
    dayEndExclusive: Instant,
    repertoireId: String,
  ): ScopedSchedulingCounts

  /**
   * Counts the live positions a recursive delete of [key] would remove, [key] included, capped at
   * [cap]. A position with a parent outside the subtree is not counted, as in
   * [proj.memorchess.axl.core.interactions.LinesExplorer.delete]; `0` when [key] is not stored.
   */
  suspend fun countDescendants(key: PositionKey, cap: Int = DESCENDANT_COUNT_CAP): Int

  /**
   * Queues [key] for the next sync push at [deviceSeq]. A repeat mark keeps the higher of the
   * stored and new [deviceSeq], so a late mark never regresses one already queued.
   */
  suspend fun markDirty(key: DirtyKey, deviceSeq: Long)

  /** Every entry currently queued for push, ordered ascending by [OutboxEntry.deviceSeq]. */
  suspend fun getOutbox(): List<OutboxEntry>

  /**
   * Removes each pushed entry of [entries], unless a later [markDirty] moved its queued
   * [OutboxEntry.deviceSeq] past the pushed one.
   */
  suspend fun clearDirty(entries: Collection<OutboxEntry>)

  /** [getPosition] including tombstones. */
  suspend fun getPositionIncludingDeleted(positionKey: PositionKey): DataNode?

  /**
   * Unconditionally writes [node] as a resolved sync winner, merging its moves like [insertNodes],
   * without queuing an outbox entry: echoing a peer's row back would loop forever.
   */
  suspend fun applyRemoteNode(node: DataNode)

  /**
   * Writes [move] without queuing an outbox entry. A move whose endpoints do not exist locally yet
   * is silently dropped.
   */
  suspend fun applyRemoteMove(move: DataMove)

  /** Retrieves a repertoire registry row, or `null` when missing or soft deleted. */
  suspend fun getRepertoire(id: String): DataRepertoire?

  /** [getRepertoire] including tombstones. */
  suspend fun getRepertoireIncludingDeleted(id: String): DataRepertoire?

  /** Every non deleted repertoire registry row. Unbounded: a user's own registry stays small. */
  suspend fun getRepertoires(): List<DataRepertoire>

  /** Inserts or replaces a repertoire registry row, queuing its own outbox entry. */
  suspend fun insertRepertoire(repertoire: DataRepertoire)

  /** Writes [repertoire] as a resolved sync winner without queuing an outbox entry. */
  suspend fun applyRemoteRepertoire(repertoire: DataRepertoire)

  /** Every live tag on the edge from [origin] to [destination], across every repertoire. */
  suspend fun getTags(origin: PositionKey, destination: PositionKey): List<DataEdgeRepertoireTag>

  /** Every live tag on a move from or to [position], across every repertoire. */
  suspend fun getTagsTouching(position: PositionKey): List<DataEdgeRepertoireTag>

  /** The tag of one `(origin, destination, repertoireId)` triple, including tombstones. */
  suspend fun getTagIncludingDeleted(
    origin: PositionKey,
    destination: PositionKey,
    repertoireId: String,
  ): DataEdgeRepertoireTag?

  /** Inserts or replaces one edge to repertoire tag row, queuing its own outbox entry. */
  suspend fun insertTag(tag: DataEdgeRepertoireTag)

  /** Writes [tag] as a resolved sync winner without queuing an outbox entry. */
  suspend fun applyRemoteTag(tag: DataEdgeRepertoireTag)

  /** Every edge of [repertoireId] whose tag and move are both live, with its SAN move. */
  suspend fun edgesTaggedWith(repertoireId: String): List<TaggedEdge>

  /**
   * Replaces [positionKey]'s trainable repertoire memberships with [repertoireIds], stamping
   * [lastReview] on each. Derived data, never synced.
   */
  suspend fun replaceTrainableRepertoires(
    positionKey: PositionKey,
    repertoireIds: Set<String>,
    lastReview: Instant?,
  )

  /** One mastery snapshot per id in [repertoireIds], zero filled for a repertoire with none. */
  suspend fun getRepertoireMasterySnapshots(
    repertoireIds: List<String>
  ): Map<String, RepertoireMasterySnapshot>
}

/** Default cap of [DatabaseQueryManager.countDescendants], shown as "or more" when reached. */
const val DESCENDANT_COUNT_CAP: Int = 500
