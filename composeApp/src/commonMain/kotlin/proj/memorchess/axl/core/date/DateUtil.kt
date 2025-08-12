package proj.memorchess.axl.core.date

import kotlin.math.absoluteValue
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
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
   * @return Current [LocalDateTime] in system timezone
   */
  fun now(): LocalDateTime {
    return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
  }

  /**
   * Returns a [LocalDateTime] representing a point far in the past (January 1, 1970).
   *
   * This is useful for initializing date fields that should represent "never" or as a default value
   * indicating no specific date has been set.
   *
   * @return [LocalDateTime] set to 1970-01-01 00:00:00
   */
  fun farInThePast(): LocalDateTime {
    return LocalDateTime(1970, 1, 1, 0, 0, 0)
  }

  /**
   * Returns today's date in the system's default timezone.
   *
   * @return Current [LocalDate] in system timezone
   */
  fun today(): LocalDate {
    return Clock.System.todayIn(TimeZone.currentSystemDefault())
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
   * Truncates a [LocalDateTime] to seconds precision by setting nanoseconds to 0.
   *
   * This extension function is useful when you need to remove sub-second precision for comparison
   * or storage purposes.
   *
   * @return [LocalDateTime] with the same date and time but nanoseconds set to 0
   */
  fun LocalDateTime.truncateToSeconds(): LocalDateTime {
    return LocalDateTime(year, monthNumber, dayOfMonth, hour, minute, second, 0)
  }

  /**
   * Compares this [LocalDateTime] with another [LocalDateTime] to check if they are almost equal.
   *
   * Two [LocalDateTime] instances are considered almost equal if their difference is less than or
   * equal to the specified [tolerance] in seconds.
   *
   * @param other The [LocalDateTime] to compare with.
   * @param tolerance The maximum difference in seconds for the two instances to be considered
   *   almost equal. Default is 1 second.
   * @return `true` if the difference between the two instances is within the [tolerance], `false`
   *   otherwise.
   */
  fun LocalDateTime.isAlmostEqual(other: LocalDateTime?, tolerance: Long = 5): Boolean {
    if (other == null) {
      return false
    }
    val difference =
      this.toInstant(TimeZone.UTC).epochSeconds - other.toInstant(TimeZone.UTC).epochSeconds
    return difference.absoluteValue <= tolerance
  }

  /**
   * Returns the maximum of two [LocalDateTime] instances.
   *
   * If one of the instances is `null`, it returns the other instance. If both are `null`, it
   * returns `null`. If both are non-null, it returns the later date.
   *
   * @param a First [LocalDateTime] instance
   * @param b Second [LocalDateTime] instance
   * @return The later [LocalDateTime], or `null` if both are `null`
   */
  fun maxOf(a: LocalDateTime?, b: LocalDateTime?): LocalDateTime? {
    return if (a == null) {
      b
    } else if (b == null) {
      a
    } else {
      if (a > b) a else b
    }
  }
}
