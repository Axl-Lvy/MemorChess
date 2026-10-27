package proj.memorchess.axl.core.scheduling

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Per card scheduling state. Outside [SchedulingAlgorithm], only [dueDate] should be read; the
 * other fields belong to the algorithm.
 *
 * @property lastReview Moment of the most recent review, or `null` for a brand new card.
 * @property firstReview Moment of the first review, or `null` if never reviewed. Never changes once
 *   set, unlike [lastReview] which learning steps update several times a day.
 * @property stability FSRS stability of the memory trace, in days.
 * @property difficulty FSRS difficulty, clamped to `[1, 10]`.
 * @property step Index into the learning or relearning step ladder; zero outside those phases.
 */
data class CardState(
  val dueDate: Instant,
  val lastReview: Instant?,
  val firstReview: Instant?,
  val stability: Double,
  val difficulty: Double,
  val reps: Int,
  val lapses: Int,
  val phase: CardPhase = CardPhase.NEW,
  val step: Int = 0,
) {

  /** Returns the [dueDate] projected onto the calendar day in [timeZone]. */
  fun dueLocalDate(timeZone: TimeZone): LocalDate = dueDate.toLocalDateTime(timeZone).date

  /** Whether the card is on a sub-day step, staying in the session until it graduates. */
  fun isInSession(): Boolean = phase == CardPhase.LEARNING || phase == CardPhase.RELEARNING
}
