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
 * Keeping the type flat means [proj.memorchess.axl.core.graph.TrainingScheduler] and the rest of
 * the codebase remain agnostic of which algorithm produced the state.
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
 * @property phase Lifecycle phase in the FSRS state machine. Sub-day learning steps keep a card in
 *   the current training session; a [CardPhase.REVIEW] phase means it is on a day grained interval.
 * @property step Index into the active learning or relearning step ladder. Meaningful only while
 *   [phase] is [CardPhase.LEARNING] or [CardPhase.RELEARNING]; zero otherwise.
 */
data class CardState(
  val dueDate: Instant,
  val lastReview: Instant?,
  val stability: Double,
  val difficulty: Double,
  val reps: Int,
  val lapses: Int,
  val phase: CardPhase = CardPhase.NEW,
  val step: Int = 0,
) {

  /** Returns the [dueDate] projected onto the calendar day in [timeZone]. */
  fun dueLocalDate(timeZone: TimeZone): LocalDate = dueDate.toLocalDateTime(timeZone).date

  /**
   * Whether the card is mid learning, i.e. on a sub-day step ([CardPhase.LEARNING] or
   * [CardPhase.RELEARNING]). Such a card stays in the active training session until a correct
   * answer graduates it to [CardPhase.REVIEW]; the scheduler keeps surfacing it regardless of
   * calendar day.
   */
  fun isInSession(): Boolean = phase == CardPhase.LEARNING || phase == CardPhase.RELEARNING
}
