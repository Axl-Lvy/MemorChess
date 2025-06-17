package proj.memorchess.axl.core.training

import kotlin.time.Duration.Companion.days
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import proj.memorchess.axl.ui.util.DateUtil

/** Calculate the next training date on success. */
object NextDateCalculatorOnSuccess : INextDateCalculator {
  override fun calculateNextDate(lastTrainedDate: LocalDate): LocalDate {
    val today = DateUtil.today()
    val passedDays = today.minus(lastTrainedDate).days
    val daysToAdd = (passedDays * 1.5).toInt() // Increase the interval by 50% on success
    return today.plus(DatePeriod(days = daysToAdd))
  }
}
