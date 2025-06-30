package proj.memorchess.axl.core.date

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Next date calculator based on the previous date. */
fun interface INextDateCalculator {

  /**
   * Calculates the next date based on the previous date.
   *
   * @param dates The previous and next date.
   * @return The next date.
   */
  fun calculateNextDate(dates: PreviousAndNextDate): LocalDate

  companion object {
    val SUCCESS = INextDateCalculator { dates ->
      val today = DateUtil.today()
      val passedDays = dates.getElapseDays()
      val daysToAdd = (passedDays * 1.5).toInt() + 1 // Increase the interval by 50% on success
      today.plus(DatePeriod(days = daysToAdd))
    }

    val FAILURE = INextDateCalculator { _ ->
      // On failure, we reset the training date interval to one day
      DateUtil.tomorrow()
    }
  }
}
