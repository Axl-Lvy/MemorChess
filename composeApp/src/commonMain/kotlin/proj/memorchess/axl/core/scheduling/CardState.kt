package proj.memorchess.axl.core.scheduling

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Per card scheduling state.
 *
 * Wave A commits to FSRS named fields directly. The shape is intentionally a flat data class rather
 * than a sealed hierarchy: any future [SchedulingAlgorithm] implementation either reinterprets
 * these fields (most SRS algorithms expose analogous concepts of stability and difficulty) or
 * stores extra state in its own internal representation while still surfacing a [dueDate] here.
 * Keeping the type flat means [proj.memorchess.axl.core.graph.TrainingSchedule] and the rest of the
 * codebase remain agnostic of which algorithm produced the state.
 *
 * The only field consumers outside of [SchedulingAlgorithm] should read is [dueDate]. All other
 * fields are owned by the algorithm.
 *
 * @property dueDate Moment when the card is next due for review.
 * @property lastReview Moment of the most recent review, or `null` for a brand new card.
 * @property stability FSRS stability of the memory trace, in days.
 * @property difficulty FSRS card difficulty, conventionally clamped to the interval one through
 *   ten.
 * @property reps Number of successful reviews recorded so far.
 * @property lapses Number of times the card has been forgotten.
 */
data class CardState(
  val dueDate: Instant,
  val lastReview: Instant?,
  val stability: Double,
  val difficulty: Double,
  val reps: Int,
  val lapses: Int,
) {

  /** Returns the [dueDate] projected onto the calendar day in [timeZone]. */
  fun dueLocalDate(timeZone: TimeZone): LocalDate = dueDate.toLocalDateTime(timeZone).date
}
