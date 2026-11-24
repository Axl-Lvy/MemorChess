package proj.memorchess.axl.core.interactions

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import proj.memorchess.axl.core.scheduling.Retrievability

/**
 * A stored move from the current position.
 *
 * @property move Move in standard algebraic notation.
 * @property review Review state of the card that trains this move, or `null` when no card does.
 */
data class Continuation(val move: String, val review: ContinuationReview?)

/**
 * Review state of the card behind a [Continuation].
 *
 * @property recall Probability that the user recalls the card now, or `null` when never reviewed.
 * @property dueDate Moment the card is next due for review.
 */
data class ContinuationReview(val recall: Retrievability?, val dueDate: Instant) {

  /** Calendar days from [today] to [dueDate] in [timeZone], zero when due today or overdue. */
  fun daysUntilDue(today: LocalDate, timeZone: TimeZone): Int =
    today.daysUntil(dueDate.toLocalDateTime(timeZone).date).coerceAtLeast(0)
}
