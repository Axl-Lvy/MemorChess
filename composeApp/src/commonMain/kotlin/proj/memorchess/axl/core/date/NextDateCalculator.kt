package proj.memorchess.axl.core.date

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import proj.memorchess.axl.core.config.ON_SUCCESS_DATE_FACTOR_SETTING

/** Next date calculator based on the previous date. */
fun interface NextDateCalculator {

  /**
   * Calculates the next date based on the previous date.
   *
   * @param dates The previous and next date.
   * @return The next date.
   */
  fun calculateNextDate(dates: PreviousAndNextDate): LocalDate

  companion object {
    val SUCCESS = NextDateCalculator { dates ->
      val factor = ON_SUCCESS_DATE_FACTOR_SETTING.getValue()
      val today = DateUtil.today()
      val passedDays = dates.getElapseDays()
      val daysToAdd = (passedDays * factor).toInt() + 1 // Increase the interval by 50% on success
      today.plus(DatePeriod(days = daysToAdd))
    }

    val FAILURE = NextDateCalculator { _ ->
      // On failure, we reset the training date interval to one day
      DateUtil.tomorrow()
    }
  }
}
