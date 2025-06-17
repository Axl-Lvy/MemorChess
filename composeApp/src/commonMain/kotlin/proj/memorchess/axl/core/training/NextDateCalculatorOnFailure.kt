package proj.memorchess.axl.core.training

import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import proj.memorchess.axl.ui.util.DateUtil

/** Calculate the next training date on failure. */
object NextDateCalculatorOnFailure : INextDateCalculator {
  override fun calculateNextDate(lastTrainedDate: LocalDate): LocalDate {
    // On failure, we reset the training date interval to one day
    return DateUtil.tomorrow()
  }
}
