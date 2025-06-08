package proj.memorchess.axl.core.training

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

object NextDateCalculatorOnFailure : INextDateCalculator {
  override fun calculateNextDate(lastTrainedDate: LocalDate): LocalDate {
    return Clock.System.todayIn(TimeZone.currentSystemDefault()).plus(DatePeriod(days = 1))
  }
}
