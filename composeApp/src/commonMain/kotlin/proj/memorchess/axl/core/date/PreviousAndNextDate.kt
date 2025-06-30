package proj.memorchess.axl.core.date

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * Represents a pair of dates: a previous date and a next date. Ensures that the previous date is
 * not after the next date.
 *
 * Provides utility methods to calculate the number of days between the two dates and to create a
 * dummy instance where both dates are set to today.
 *
 * @property previousDate The earlier date in the pair.
 * @property nextDate The later date in the pair.
 */
data class PreviousAndNextDate(val previousDate: LocalDate, val nextDate: LocalDate) {

  /**
   * Checks that previousDate is not after nextDate upon initialization. Throws an
   * IllegalArgumentException if the condition is not met.
   */
  init {
    check(previousDate <= nextDate) { "Previous date must be before or equal to next date" }
  }

  /** Returns the number of days between previousDate and nextDate. The result is non-negative. */
  fun getElapseDays(): Int {
    return previousDate.daysUntil(nextDate)
  }

  companion object {

    /** Creates a dummy PreviousAndNextDate instance where both dates are set to today. */
    fun dummyToday(): PreviousAndNextDate {
      val today = DateUtil.today()
      return PreviousAndNextDate(today, today)
    }
  }
}
