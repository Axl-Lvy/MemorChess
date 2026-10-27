package proj.memorchess.axl.core.date

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/** Common date and time operations. */
object DateUtil {

  /** Returns the current instant. */
  fun now(): Instant {
    return Clock.System.now()
  }

  /** Returns today's date in the system's default timezone. */
  fun today(): LocalDate {
    return today(TimeZone.currentSystemDefault())
  }

  /** Returns today's date in [timeZone]. */
  fun today(timeZone: TimeZone): LocalDate {
    return Clock.System.todayIn(timeZone)
  }

  /** Drops the sub-second part of this instant. */
  fun Instant.truncateToSeconds(): Instant {
    val timeZone = TimeZone.currentSystemDefault()
    return this.toLocalDateTime(timeZone)
      .let { LocalDateTime(it.year, it.month.number, it.day, it.hour, it.minute, it.second) }
      .toInstant(timeZone)
  }
}
