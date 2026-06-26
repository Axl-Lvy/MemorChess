package proj.memorchess.axl.core.data

/**
 * Bounded daily scheduling tallies for a single calendar day.
 *
 * Every field is a `COUNT(*)` over an indexed predicate. No row set crosses the persistence seam to
 * produce these numbers, so the cost is constant regardless of repertoire size. Computed by
 * [DatabaseQueryManager.getSchedulingCounts] and consumed by
 * [proj.memorchess.axl.core.graph.TrainingScheduler] to enforce the daily caps and report the
 * pending count without ever enumerating the graph.
 *
 * @property introducedToday Number of cards whose `firstReview` falls inside the day window
 *   `[dayStart, dayEndExclusive)`. Drives the new card cap.
 * @property trainedToday Number of cards whose `lastReview` falls inside the day window `[dayStart,
 *   dayEndExclusive)`. Drives the total reviews cap.
 * @property dueReviews Number of trainable graduated cards due on or before the day:
 *   `hasGoodOutgoing AND phase = REVIEW AND dueDate < dayEndExclusive`.
 * @property dueNew Number of trainable brand new cards due on or before the day: `hasGoodOutgoing
 *   AND phase = NEW AND dueDate < dayEndExclusive`.
 * @property inSession Number of trainable cards currently mid learning: `hasGoodOutgoing AND phase
 *   IN (LEARNING, RELEARNING)`. These are exempt from the daily caps.
 */
data class SchedulingCounts(
  val introducedToday: Int,
  val trainedToday: Int,
  val dueReviews: Int,
  val dueNew: Int,
  val inSession: Int,
)
