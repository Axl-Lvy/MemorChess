package proj.memorchess.axl.core.graph

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionKey
import proj.memorchess.axl.core.date.DateUtil
import proj.memorchess.axl.core.scheduling.ReviewGrade
import proj.memorchess.axl.core.scheduling.SchedulingAlgorithm

/**
 * Spaced repetition driver for the opening tree.
 *
 * Drives entirely off the bounded scheduling surface of [DatabaseQueryManager]: a single
 * [DatabaseQueryManager.getSchedulingCounts] call plus a constant number of `LIMIT 1` lookups per
 * selection. It holds no graph and never enumerates the repertoire, so its cost stays constant no
 * matter how large the repertoire grows. Writing a review result back goes through [TreeStore],
 * which owns the only mutation path.
 *
 * A position is trainable when it has at least one outgoing edge marked as good, captured by the
 * derived `hasGoodOutgoing` column the queries filter on.
 *
 * Two daily caps bound a session, both counted on distinct positions within the calendar day in
 * [timeZone]:
 * - [maxNewMovesPerDay] caps positions whose very first review happens today, counted through
 *   [proj.memorchess.axl.core.data.SchedulingCounts.introducedToday];
 * - [maxTotalMovesPerDay] caps everything trained today, counted through
 *   [proj.memorchess.axl.core.data.SchedulingCounts.trainedToday].
 *
 * Cards mid learning steps are exempt: like Anki, a card that entered the session finishes its
 * sub-day steps even when the caps are reached. The counts derive from the persisted card states on
 * every call, so they reset at local midnight on their own. Training ahead (passing a future [day])
 * gets a fresh budget for that day by design, matching Anki's study-ahead behavior.
 *
 * The caps are suppliers rather than flat values so a settings change takes effect immediately on
 * the long lived singleton; defaults are unlimited so that direct construction stays usable without
 * any configuration wiring.
 */
class TrainingScheduler(
  private val database: DatabaseQueryManager,
  private val treeStore: TreeStore,
  private val algorithm: SchedulingAlgorithm,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
  private val maxNewMovesPerDay: () -> Int = { Int.MAX_VALUE },
  private val maxTotalMovesPerDay: () -> Int = { Int.MAX_VALUE },
) {

  /**
   * Returns the next [TrainingEntry] to train, or `null` when there is nothing left for [day].
   *
   * One [DatabaseQueryManager.getSchedulingCounts] call establishes the day's remaining budgets,
   * then the four bounded query tiers are asked in order so the in-session learning loop drains in
   * step order without starving the day's review queue:
   * 1. **Ready learning cards** — a [proj.memorchess.axl.core.scheduling.CardPhase.LEARNING] or
   *    `RELEARNING` card whose sub-day due moment has arrived. The earliest due wins, so a card on
   *    a one minute step surfaces before one on a ten minute step. Exempt from the caps.
   * 2. **Review cards** due on or before [day] — picked by smallest graph depth to follow the
   *    natural opening order. Consumes the total budget.
   * 3. **New cards** due on or before [day] — picked by `depth ASC, createdAt ASC`, so shallower
   *    positions surface first with ties broken by when the position was first added. Consumes the
   *    new ∩ total budget.
   * 4. **Pending learning cards** — sub-day cards not yet due. Falling through to these guarantees
   *    a just-failed card always resurfaces in the same session rather than ending it early, again
   *    earliest-due first. Exempt from the caps.
   *
   * The entry is **not** removed from the schedule: a card leaves the session only when [grade]
   * graduates it to a day grained review interval.
   */
  suspend fun nextDue(day: LocalDate = DateUtil.today()): TrainingEntry? {
    val now = DateUtil.now()
    val (dayStart, dayEnd) = dayBounds(day)
    val counts = database.getSchedulingCounts(dayStart, dayEnd)
    val newRemaining = (maxNewMovesPerDay() - counts.introducedToday).coerceAtLeast(0)
    val totalRemaining = (maxTotalMovesPerDay() - counts.trainedToday).coerceAtLeast(0)

    // Tier 1: in-session, due now — exempt from caps.
    database.nextReadyLearningCard(now)?.let {
      return it
    }
    // Tier 2: due reviews — consume total budget.
    if (totalRemaining > 0) {
      database.nextDueReviewCard(dayEnd)?.let {
        return it
      }
    }
    // Tier 3: due new — consume new ∩ total budget.
    if (newRemaining > 0 && totalRemaining > 0) {
      database.nextDueNewCard(dayEnd)?.let {
        return it
      }
    }
    // Tier 4: in-session, not yet due — exempt from caps.
    return database.nextPendingLearningCard(now)
  }

  /**
   * Returns a trainable entry reachable from one of [position]'s outgoing edges, or `null` when
   * none qualifies. Picking such an entry preserves the natural opening order during a session.
   *
   * Resolves [position]'s outgoing destinations (bounded by the branching factor) from the cache,
   * then asks the database for the first eligible one through
   * [DatabaseQueryManager.findEligibleAmong] so no graph enumeration happens.
   */
  suspend fun nextAfter(position: PositionKey, day: LocalDate = DateUtil.today()): TrainingEntry? {
    val node = treeStore.current().get(position) ?: return null
    val destinations = node.outgoing.values.map { it.to }
    val (_, dayEnd) = dayBounds(day)
    return database.findEligibleAmong(destinations, dayEnd)
  }

  /**
   * Counts entries still to train for [day]: in-session cards plus the servable due reviews and new
   * cards once the daily caps are applied. Computed from a single
   * [DatabaseQueryManager.getSchedulingCounts] call, so its cost is constant regardless of
   * repertoire size, and it reports what the trainer will actually serve.
   */
  suspend fun pendingCount(day: LocalDate = DateUtil.today()): Int {
    val (dayStart, dayEnd) = dayBounds(day)
    val c = database.getSchedulingCounts(dayStart, dayEnd)
    val totalRemaining = (maxTotalMovesPerDay() - c.trainedToday).coerceAtLeast(0)
    val newRemaining = (maxNewMovesPerDay() - c.introducedToday).coerceAtLeast(0)
    val reviewsServable = minOf(c.dueReviews, totalRemaining)
    val newBudget = minOf(newRemaining, totalRemaining - reviewsServable).coerceAtLeast(0)
    val newServable = minOf(c.dueNew, newBudget)
    return c.inSession + reviewsServable + newServable
  }

  /**
   * Persists the result of a review for [position]. Computes the next
   * [proj.memorchess.axl.core.scheduling.CardState] through [SchedulingAlgorithm] and stores it
   * through [TreeStore].
   */
  suspend fun grade(position: PositionKey, grade: ReviewGrade) {
    val node = treeStore.current().get(position) ?: return
    val now = DateUtil.now()
    val nextState = algorithm.schedule(node.cardState, grade, now)
    treeStore.updateCardState(position, nextState)
  }

  /**
   * Computes the half open day window `[dayStart, dayEndExclusive)` for [day] in [timeZone].
   *
   * `dayStart` is local midnight of [day]; `dayEndExclusive` is local midnight of the following
   * day. "Due on or before [day]" is `dueDate < dayEndExclusive`; "happened on [day]" is `>=
   * dayStart && < dayEndExclusive`.
   */
  private fun dayBounds(day: LocalDate): Pair<Instant, Instant> {
    val dayStart = day.atStartOfDayIn(timeZone)
    val dayEndExclusive = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
    return dayStart to dayEndExclusive
  }
}
