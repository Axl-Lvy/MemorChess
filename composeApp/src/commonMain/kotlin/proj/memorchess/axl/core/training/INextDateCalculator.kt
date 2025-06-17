package proj.memorchess.axl.core.training

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import proj.memorchess.axl.ui.util.DateUtil

/** Next date calculator based on the previous date. */
fun interface INextDateCalculator {

  /**
   * Calculates the next date based on the previous date.
   *
   * @param lastTrainedDate The previous date.
   * @return The next date.
   */
  fun calculateNextDate(lastTrainedDate: LocalDate): LocalDate

  companion object {
    val SUCCESS = INextDateCalculator { lastTrainedDate ->
      val today = DateUtil.today()
      val passedDays = today.minus(lastTrainedDate).days
      val daysToAdd = (passedDays * 1.5).toInt() // Increase the interval by 50% on success
      today.plus(DatePeriod(days = daysToAdd))
    }

    val FAILURE = INextDateCalculator { _ ->
      // On failure, we reset the training date interval to one day
      DateUtil.tomorrow()
    }
  }
}
