package proj.memorchess.axl.core.interaction

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import proj.memorchess.axl.core.interactions.ContinuationReview

class TestContinuationReview {

  private val today = LocalDate(2026, 11, 24)

  private fun dueAt(instant: String) =
    ContinuationReview(recall = null, dueDate = Instant.parse(instant))

  @Test
  fun dueEarlierTodayIsZeroDays() {
    dueAt("2026-11-24T08:00:00Z").daysUntilDue(today, TimeZone.UTC) shouldBe 0
  }

  @Test
  fun overdueIsZeroDays() {
    dueAt("2026-11-21T12:00:00Z").daysUntilDue(today, TimeZone.UTC) shouldBe 0
  }

  @Test
  fun dueTomorrowIsOneDay() {
    dueAt("2026-11-25T12:00:00Z").daysUntilDue(today, TimeZone.UTC) shouldBe 1
  }

  @Test
  fun dueFarAheadCountsEveryDay() {
    val due = today.plus(DatePeriod(days = 400)).atStartOfDayIn(TimeZone.UTC)

    ContinuationReview(recall = null, dueDate = due).daysUntilDue(today, TimeZone.UTC) shouldBe 400
  }

  @Test
  fun daysAreCountedInTheGivenTimeZone() {
    val review = dueAt("2026-11-24T23:30:00Z")

    review.daysUntilDue(today, TimeZone.of("Europe/Paris")) shouldBe 1
    review.daysUntilDue(today, TimeZone.UTC) shouldBe 0
  }
}
