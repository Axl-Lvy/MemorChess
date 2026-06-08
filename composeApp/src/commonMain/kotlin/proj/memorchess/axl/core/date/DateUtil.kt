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

/**
 * Utility object providing common date and time operations using kotlinx-datetime.
 *
 * This object centralizes date/time functionality for the application, providing convenient methods
 * for getting current time, calculating date differences, and performing date arithmetic
 * operations.
 */
object DateUtil {

  /**
   * Returns the current date and time in the system's default timezone.
   *
   * @return Current [Instant] in system timezone
   */
  fun now(): Instant {
    return Clock.System.now()
  }

  /**
   * Returns today's date in the system's default timezone.
   *
   * @return Current [LocalDate] in system timezone
   */
  fun today(): LocalDate {
    val todayIn = Clock.System.todayIn(TimeZone.currentSystemDefault())
    return todayIn
  }

  /**
   * Truncates a [Instant] to seconds precision by setting nanoseconds to 0.
   *
   * This extension function is useful when you need to remove sub-second precision for comparison
   * or storage purposes.
   *
   * @return [Instant] with the same date and time but nanoseconds set to 0
   */
  fun Instant.truncateToSeconds(): Instant {
    val timeZone = TimeZone.currentSystemDefault()
    return this.toLocalDateTime(timeZone)
      .let { LocalDateTime(it.year, it.month.number, it.day, it.hour, it.minute, it.second) }
      .toInstant(timeZone)
  }
}
