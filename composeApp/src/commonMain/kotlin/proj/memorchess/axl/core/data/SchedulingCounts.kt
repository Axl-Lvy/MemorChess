package proj.memorchess.axl.core.data

/**
 * Scheduling tallies for one calendar day, each a `COUNT(*)` over an indexed predicate.
 *
 * @property introducedToday Cards whose `firstReview` is in `[dayStart, dayEndExclusive)`.
 * @property trainedToday Cards whose `lastReview` is in `[dayStart, dayEndExclusive)`.
 * @property dueReviews Trainable `REVIEW` cards with `dueDate < dayEndExclusive`.
 * @property dueNew Trainable `NEW` cards with `dueDate < dayEndExclusive`.
 * @property inSession Trainable `LEARNING`/`RELEARNING` cards; exempt from the daily caps.
 */
data class SchedulingCounts(
  val introducedToday: Int,
  val trainedToday: Int,
  val dueReviews: Int,
  val dueNew: Int,
  val inSession: Int,
)

/**
 * [SchedulingCounts] narrowed to one repertoire, without the daily cap tallies since those stay
 * global.
 */
data class ScopedSchedulingCounts(val dueReviews: Int, val dueNew: Int, val inSession: Int)
