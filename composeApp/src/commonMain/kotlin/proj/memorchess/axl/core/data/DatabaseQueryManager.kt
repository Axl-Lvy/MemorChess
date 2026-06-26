package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.graph.DeleteMode
import proj.memorchess.axl.core.graph.TrainingEntry

/**
 * Low level persistence seam for the opening tree.
 *
 * Only [proj.memorchess.axl.core.graph.TreeStore] and the platform specific implementations are
 * expected to touch this interface. The rest of the application talks to
 * [proj.memorchess.axl.core.graph.TreeStore].
 */
interface DatabaseQueryManager {

  /**
   * Retrieves all stored positions.
   *
   * @param withDeletedOnes When `true`, includes rows flagged with [DataNode.isDeleted].
   */
  suspend fun getAllNodes(withDeletedOnes: Boolean = false): List<DataNode>

  /** Retrieves a specific position, or `null` when missing or soft deleted. */
  suspend fun getPosition(positionKey: PositionKey): DataNode?

  /**
   * Deletes a single position and any incident moves.
   *
   * @param position Position to remove.
   * @param mode See [DeleteMode]. [DeleteMode.HARD] physically removes the row.
   */
  suspend fun deletePosition(position: PositionKey, mode: DeleteMode = DeleteMode.HARD)

  /**
   * Deletes a single move.
   *
   * @param origin Origin of the move.
   * @param move Move in standard algebraic notation.
   * @param mode See [DeleteMode]. [DeleteMode.HARD] physically removes the row.
   */
  suspend fun deleteMove(origin: PositionKey, move: String, mode: DeleteMode = DeleteMode.HARD)

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
}

/**
 * Default upper bound for [DatabaseQueryManager.countDescendants]. A delete confirmation that would
 * remove this many or more positions is shown as a "[DESCENDANT_COUNT_CAP] or more" sentinel rather
 * than paging the whole subtree to produce an exact number.
 */
const val DESCENDANT_COUNT_CAP: Int = 500
