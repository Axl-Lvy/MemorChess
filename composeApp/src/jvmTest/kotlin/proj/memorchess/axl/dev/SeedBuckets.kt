package proj.memorchess.axl.dev

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import proj.memorchess.axl.core.scheduling.CardPhase
import proj.memorchess.axl.core.scheduling.CardState

/** Counts [states] in each bucket a lived-in seed must fill, as seen at [now] in [zone]. */
fun seedBuckets(states: Collection<CardState>, now: Instant, zone: TimeZone): Map<String, Int> {
  val today = now.toLocalDateTime(zone).date
  val todayStart = today.atStartOfDayIn(zone)
  val tomorrowStart = today.plus(DatePeriod(days = 1)).atStartOfDayIn(zone)
  val review = states.filter { it.phase == CardPhase.REVIEW }
  return mapOf(
    "new" to states.count { it.phase == CardPhase.NEW },
    "learning" to
      states.count { it.phase == CardPhase.LEARNING || it.phase == CardPhase.RELEARNING },
    "dueToday" to review.count { it.dueDate >= todayStart && it.dueDate < tomorrowStart },
    "overdue" to review.count { it.dueDate < todayStart },
    "mature" to
      review.count { state -> state.lastReview?.let { state.dueDate - it >= 21.days } == true },
  )
}
