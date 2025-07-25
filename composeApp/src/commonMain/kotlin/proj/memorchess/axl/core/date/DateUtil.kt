package proj.memorchess.axl.core.date

import kotlin.math.absoluteValue
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

object DateUtil {

  fun yesterday(): LocalDate {
    return dateInDays(-1)
  }

  fun now(): LocalDateTime {
    return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
  }

  fun today(): LocalDate {
    return Clock.System.todayIn(TimeZone.currentSystemDefault())
  }

  fun tomorrow(): LocalDate {
    return dateInDays(1)
  }

  fun dateInDays(days: Int): LocalDate {
    return today().plus(DatePeriod(days = days))
  }

  fun daysUntil(to: LocalDate): Int {
    val today = today()
    return today.daysUntil(to).absoluteValue
  }

  fun LocalDateTime.truncateToSeconds(): LocalDateTime {
    return LocalDateTime(year, monthNumber, dayOfMonth, hour, minute, second, 0)
  }
}
