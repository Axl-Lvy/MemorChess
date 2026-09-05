package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.graph.DeleteMode
import proj.memorchess.axl.core.graph.TrainingEntry

/**
 * Low level persistence seam for the opening tree.
 *
 * Only [proj.memorchess.axl.core.graph.TreeStore] and the platform specific implementations are
 * expected to touch the node and move surface of this interface; the rest of the application talks
 * to [proj.memorchess.axl.core.graph.TreeStore]. The outbox surface ([markDirty], [getOutbox],
 * [clearDirty]) is the exception: [proj.memorchess.axl.core.config.ConfigItem] implementations call
 * [markDirty] directly to queue a setting's [DirtyKey.SettingKey], since a setting has no row of
 * its own for [proj.memorchess.axl.core.graph.TreeStore] to write through.
 */
interface DatabaseQueryManager {

  /** Retrieves a specific position, or `null` when missing or soft deleted. */
  suspend fun getPosition(positionKey: PositionKey): DataNode?

  /**
   * Reads one bounded page of non deleted nodes ordered by position key ascending.
   *
   * This is the only multi row read on the seam and it is bounded by [limit] plus an explicit
   * [cursor], so no query ever pulls the whole store into memory. Each returned node carries its
   * edges, exactly as a single read would reconstruct them.
   *
   * Paging contract: pass `null` as [cursor] for the first page; thereafter pass the previous
   * page's [NodesPage.nextCursor]. The page contains the rows with `positionKey > cursor` ordered
   * ascending, capped at [limit]. [NodesPage.nextCursor] is the last returned node's position key
   * when a full [limit] sized page was returned (more rows may remain), and `null` once a partial
   * page is returned, which terminates the loop. A store size that is an exact multiple of [limit]
   * therefore ends with one trailing empty page whose cursor is `null`.
   *
   * @param cursor The position key of the last node of the previous page, or `null` for the first
   *   page.
   * @param limit The maximum number of nodes to return; must be strictly positive.
   * @throws IllegalArgumentException when [limit] is not strictly positive.
   */
  suspend fun getNodesPage(cursor: String?, limit: Int): NodesPage

  /**
   * Deletes a single position and any incident moves.
   *
   * @param position Position to remove.
   * @param mode See [DeleteMode]. [DeleteMode.HARD] physically removes the row.
   * @param originDevice Device stamped on the tombstone when [mode] is [DeleteMode.SOFT].
   * @param deviceSeq That device's write counter, stamped alongside [originDevice].
   * @param updatedAt Moment the tombstone was written.
   */
  suspend fun deletePosition(
    position: PositionKey,
    mode: DeleteMode = DeleteMode.SOFT,
    originDevice: String = "",
    deviceSeq: Long = 0L,
    updatedAt: Instant = DateUtil.now(),
  )

  /**
   * Deletes a single move.
   *
   * @param origin Origin of the move.
   * @param move Move in standard algebraic notation.
   * @param mode See [DeleteMode]. [DeleteMode.HARD] physically removes the row.
   * @param originDevice Device stamped on the tombstone when [mode] is [DeleteMode.SOFT].
   * @param deviceSeq That device's write counter, stamped alongside [originDevice].
   * @param updatedAt Moment the tombstone was written.
   */
  suspend fun deleteMove(
    origin: PositionKey,
    move: String,
    mode: DeleteMode = DeleteMode.SOFT,
    originDevice: String = "",
    deviceSeq: Long = 0L,
    updatedAt: Instant = DateUtil.now(),
  )

  /** Hard wipe of every node and move. */
  suspend fun eraseAll()

  /**
   * Inserts new positions.
   *
   * @param positions The [DataNode] objects to insert.
   */
  suspend fun insertNodes(vararg positions: DataNode)

  /** Retrieves the latest `updatedAt` across nodes and moves. */
  suspend fun getLastUpdate(): Instant?

  /**
   * Bounded `LIMIT 1` lookup of the next ready in session card.
   *
   * Returns the trainable card currently mid learning whose due date has already arrived, ordered
   * by the earliest due date, or `null` when none qualifies. Predicate: `hasGoodOutgoing AND phase
   * IN (LEARNING, RELEARNING) AND dueDate <= now`, ordered by `dueDate ASC`. Soft deleted rows are
   * excluded. No edges are loaded; only the columns a
   * [proj.memorchess.axl.core.graph.TrainingEntry] needs are read.
   *
   * @param now Current instant. The due bound is inclusive, so a card due exactly at [now]
   *   qualifies.
   */
  suspend fun nextReadyLearningCard(now: Instant): TrainingEntry?

  /**
   * Bounded `LIMIT 1` lookup of the next pending in session card.
   *
   * Returns the trainable card currently mid learning whose due date is still in the future,
   * ordered by the earliest due date, or `null` when none qualifies. Predicate: `hasGoodOutgoing
   * AND phase IN (LEARNING, RELEARNING) AND dueDate > now`, ordered by `dueDate ASC`. Soft deleted
   * rows are excluded.
   *
   * @param now Current instant. The due bound is strict, so a card due exactly at [now] does not
   *   qualify (it is ready, not pending).
   */
  suspend fun nextPendingLearningCard(now: Instant): TrainingEntry?

  /**
   * Bounded `LIMIT 1` lookup of the next due review card.
   *
   * Returns the trainable graduated card due on or before the day, shallowest first, or `null` when
   * none qualifies. Predicate: `hasGoodOutgoing AND phase = REVIEW AND dueDate < dayEndExclusive`,
   * ordered by `depth ASC`. Soft deleted rows are excluded.
   *
   * @param dayEndExclusive Start of the day after the target day. A card due exactly at this
   *   instant belongs to the next day and is excluded.
   */
  suspend fun nextDueReviewCard(dayEndExclusive: Instant): TrainingEntry?

  /**
   * Bounded `LIMIT 1` lookup of the next due new card.
   *
   * Returns the trainable brand new card due on or before the day, shallowest first with ties
   * broken by the earliest creation, or `null` when none qualifies. Predicate: `hasGoodOutgoing AND
   * phase = NEW AND dueDate < dayEndExclusive`, ordered by `depth ASC, createdAt ASC`. Soft deleted
   * rows are excluded.
   *
   * @param dayEndExclusive Start of the day after the target day. A card due exactly at this
   *   instant belongs to the next day and is excluded.
   */
  suspend fun nextDueNewCard(dayEndExclusive: Instant): TrainingEntry?

  /**
   * Computes the day's bounded scheduling tallies as five `COUNT(*)` queries over indexed
   * predicates. No row set crosses the seam. See [SchedulingCounts] for each field's predicate.
   *
   * @param dayStart Start of the target calendar day in the active time zone.
   * @param dayEndExclusive Start of the following day, used as the exclusive upper bound for all
   *   day windowed predicates.
   */
  suspend fun getSchedulingCounts(dayStart: Instant, dayEndExclusive: Instant): SchedulingCounts

  /**
   * Bounded lookup of the first eligible card among an explicit, bounded set of positions.
   *
   * Used to find the next position to train after the current one without enumerating the graph:
   * the caller supplies the current node's outgoing destinations (bounded by the branching factor).
   * A position is eligible when it is trainable and either mid learning or due on or before the
   * day: `positionKey IN (keys) AND hasGoodOutgoing AND (phase IN (LEARNING, RELEARNING) OR dueDate
   * < dayEndExclusive)`. Soft deleted rows are excluded. Returns the first eligible entry, or
   * `null` when [keys] is empty or none qualify.
   *
   * @param keys The bounded set of candidate positions to consider.
   * @param dayEndExclusive Start of the day after the target day, the exclusive due bound.
   */
  suspend fun findEligibleAmong(keys: List<PositionKey>, dayEndExclusive: Instant): TrainingEntry?

  /**
   * Counts the non-deleted positions in the subtree reachable from [key] that a recursive delete
   * would remove, [key] itself included.
   *
   * A descendant is counted only when [key]'s subtree is its sole set of parents: a convergent
   * position reachable through a parent outside the visited subtree is not counted (and not
   * descended into), matching the cascade rule of
   * [proj.memorchess.axl.core.interactions.LinesExplorer.delete]. The walk is a bounded breadth
   * first traversal over the move edges, identical across every backend so the convergence rule
   * cannot drift.
   *
   * Bounded by [cap]: the traversal stops enqueuing once [cap] positions have been counted, so a
   * subtree at or above [cap] returns exactly [cap]. The UI renders that as a "cap or more"
   * sentinel instead of paying for an unbounded walk. Returns `0` when [key] is not stored.
   *
   * @param key Root of the subtree to count.
   * @param cap Upper bound on the returned count; the traversal never visits more than [cap]
   *   positions.
   */
  suspend fun countDescendants(key: PositionKey, cap: Int = DESCENDANT_COUNT_CAP): Int

  /**
   * Queues [key] for the next sync push at [deviceSeq], or refreshes it if already queued.
   * Collapsing repeat edits into one entry is why the outbox stores keys rather than rows. A repeat
   * mark keeps the higher of the stored and new [deviceSeq], so a mark that arrives late never
   * regresses one already queued.
   */
  suspend fun markDirty(key: DirtyKey, deviceSeq: Long)

  /** Every entry currently queued for push, ordered ascending by [OutboxEntry.deviceSeq]. */
  suspend fun getOutbox(): List<OutboxEntry>

  /**
   * Removes each of [entries] once it has been pushed, but only when the entry's queued
   * [OutboxEntry.deviceSeq] has not moved past the one that was actually pushed: a [markDirty]
   * landing between the read that produced [entries] and this call survives instead of being
   * silently dropped.
   */
  suspend fun clearDirty(entries: Collection<OutboxEntry>)

  /**
   * Reads [positionKey] ignoring the soft-delete filter [getPosition] applies, so a caller can
   * compare it against a pulled sync row even when the local copy is a tombstone. Used only by
   * [proj.memorchess.axl.core.graph.TreeStore]'s pull-apply path, never by application code.
   */
  suspend fun getPositionIncludingDeleted(positionKey: PositionKey): DataNode?

  /**
   * Writes [node]'s scalar fields as the resolved winner of a sync conflict (see
   * [proj.memorchess.axl.core.sync.SyncEngine]), merging rather than replacing
   * [DataNode.previousAndNextMoves] exactly like [insertNodes], but **without** queuing an outbox
   * entry: the row came from a peer's own push, so echoing it back would loop forever. The caller
   * is responsible for having already run conflict resolution; this is an unconditional write.
   */
  suspend fun applyRemoteNode(node: DataNode)

  /**
   * Writes [move] as the resolved winner of a sync conflict, without queuing an outbox entry, for
   * the same reason as [applyRemoteNode]. [move]'s origin and destination nodes must already exist
   * locally (created by an earlier [applyRemoteNode] call in the same pull, or already present); a
   * move whose endpoint does not yet exist is silently dropped, matching how a normal move write
   * already assumes its endpoints are resolvable.
   */
  suspend fun applyRemoteMove(move: DataMove)

  /** Retrieves a repertoire registry row, or `null` when missing or soft deleted. */
  suspend fun getRepertoire(id: String): DataRepertoire?

  /**
   * [getRepertoire] ignoring the soft delete filter, so a caller can compare it against a pulled
   * sync row even when the local copy is a tombstone. Used only by [proj.memorchess.axl.core.graph.TreeStore]'s pull apply path.
   */
  suspend fun getRepertoireIncludingDeleted(id: String): DataRepertoire?

  /** Every non deleted repertoire registry row. Unbounded: a user's own registry stays small. */
  suspend fun getRepertoires(): List<DataRepertoire>

  /** Inserts or replaces a repertoire registry row, queuing its own outbox entry. */
  suspend fun insertRepertoire(repertoire: DataRepertoire)

  /**
   * Writes [repertoire] as the resolved winner of a sync conflict, without queuing an outbox entry,
   * for the same reason as [applyRemoteNode].
   */
  suspend fun applyRemoteRepertoire(repertoire: DataRepertoire)

  /** Every live tag on the edge from [origin] to [destination], across every repertoire. */
  suspend fun getTags(origin: PositionKey, destination: PositionKey): List<DataEdgeRepertoireTag>

  /**
   * [getTags] ignoring the soft delete filter for one `(origin, destination, repertoireId)` triple,
   * so a caller can compare it against a pulled sync row even when the local copy is a tombstone.
   * Used only by [proj.memorchess.axl.core.graph.TreeStore]'s pull apply path.
   */
  suspend fun getTagIncludingDeleted(
    origin: PositionKey,
    destination: PositionKey,
    repertoireId: String,
  ): DataEdgeRepertoireTag?

  /** Inserts or replaces one edge to repertoire tag row, queuing its own outbox entry. */
  suspend fun insertTag(tag: DataEdgeRepertoireTag)

  /**
   * Writes [tag] as the resolved winner of a sync conflict, without queuing an outbox entry, for
   * the same reason as [applyRemoteNode].
   */
  suspend fun applyRemoteTag(tag: DataEdgeRepertoireTag)
}

/**
 * Default upper bound for [DatabaseQueryManager.countDescendants]. A delete confirmation that would
 * remove this many or more positions is shown as a "[DESCENDANT_COUNT_CAP] or more" sentinel rather
 * than paging the whole subtree to produce an exact number.
 */
const val DESCENDANT_COUNT_CAP: Int = 500
