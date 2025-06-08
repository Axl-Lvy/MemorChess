package proj.memorchess.axl.core.training

import kotlinx.datetime.LocalDate

/** Next date calculator based on the previous date. */
fun interface INextDateCalculator {

  /**
   * Calculates the next date based on the previous date.
   *
   * @param lastTrainedDate The previous date.
   * @return The next date.
   */
  fun calculateNextDate(lastTrainedDate: LocalDate): LocalDate
}
