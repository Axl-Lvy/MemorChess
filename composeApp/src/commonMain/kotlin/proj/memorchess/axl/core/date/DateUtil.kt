package proj.memorchess.axl.core.date

import kotlin.math.absoluteValue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.plus
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
   * Returns a [Instant] representing a point far in the past (January 1, 1970).
   *
   * This is useful for initializing date fields that should represent "never" or as a default value
   * indicating no specific date has been set.
   *
   * @return [Instant] set to 1970-01-01 00:00:00
   */
  fun farInThePast(): Instant {
    return Instant.parse("1970-01-01T00:00:00Z")
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
   * Returns tomorrow's date.
   *
   * @return [LocalDate] representing tomorrow
   */
  fun tomorrow(): LocalDate {
    return dateInDays(1)
  }

  /**
   * Returns a date that is the specified number of days from today.
   *
   * @param days Number of days to add to today's date. Can be negative for past dates
   * @return [LocalDate] representing the date [days] days from today
   */
  fun dateInDays(days: Int): LocalDate {
    return today().plus(DatePeriod(days = days))
  }

  /**
   * Calculates the absolute number of days between today and the specified date.
   *
   * @param to Target date to calculate days until
   * @return Absolute number of days between today and [to]
   */
  fun daysUntil(to: LocalDate): Int {
    val today = today()
    return today.daysUntil(to).absoluteValue
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

  /**
   * Compares this [Instant] with another [Instant] to check if they are almost equal.
   *
   * Two [Instant] instances are considered almost equal if their difference is less than or equal
   * to the specified [tolerance] in seconds.
   *
   * @param other The [Instant] to compare with.
   * @param tolerance The maximum difference in seconds for the two instances to be considered
   *   almost equal. Default is 1 second.
   * @return `true` if the difference between the two instances is within the [tolerance], `false`
   *   otherwise.
   */
  fun Instant.isAlmostEqual(other: Instant?, tolerance: Long = 5): Boolean {
    if (other == null) {
      return false
    }
    val difference = this.epochSeconds - other.epochSeconds
    return difference.absoluteValue <= tolerance
  }

  /**
   * Returns the maximum of two [Instant] instances.
   *
   * If one of the instances is `null`, it returns the other instance. If both are `null`, it
   * returns `null`. If both are non-null, it returns the later date.
   *
   * @param a First [Instant] instance
   * @param b Second [Instant] instance
   * @return The later [Instant], or `null` if both are `null`
   */
  fun maxOf(a: Instant?, b: Instant?): Instant? {
    return if (a == null) {
      b
    } else if (b == null) {
      a
    } else {
      if (a > b) a else b
    }
  }

  fun LocalDateTime.toInstant(): Instant {
    return this.toInstant(TimeZone.currentSystemDefault())
  }
}
