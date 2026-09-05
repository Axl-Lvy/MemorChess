package proj.memorchess.axl.core.streak

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import proj.memorchess.axl.test_util.InMemoryDailyActivityStore

class TestStreakTracker {

  private val day1 = LocalDate(2026, 9, 1)

  @Test
  fun cardsCompletedTodayIsZeroWithNoReview() = runTest {
    val tracker = StreakTracker(InMemoryDailyActivityStore())

    tracker.cardsCompletedToday(day1) shouldBe 0
  }

  @Test
  fun cardsCompletedTodayIsOneAfterOneReview() = runTest {
    val tracker = StreakTracker(InMemoryDailyActivityStore())

    tracker.recordReview(day1)

    tracker.cardsCompletedToday(day1) shouldBe 1
  }

  @Test
  fun cardsCompletedTodayCountsEveryRecordedReview() = runTest {
    val tracker = StreakTracker(InMemoryDailyActivityStore())

    tracker.recordReview(day1)
    tracker.recordReview(day1)
    tracker.recordReview(day1)

    tracker.cardsCompletedToday(day1) shouldBe 3
  }

  @Test
  fun streakDaysIsZeroWithNoReviewAndNoPriorActivity() = runTest {
    val tracker = StreakTracker(InMemoryDailyActivityStore())

    tracker.streakDays(day1) shouldBe 0
  }

  @Test
  fun streakDaysIsOneAfterFirstReview() = runTest {
    val tracker = StreakTracker(InMemoryDailyActivityStore())

    tracker.recordReview(day1)

    tracker.streakDays(day1) shouldBe 1
  }

  @Test
  fun streakDaysExtendsOnConsecutiveActiveDays() = runTest {
    val tracker = StreakTracker(InMemoryDailyActivityStore())
    val day2 = day1.plus(1, DateTimeUnit.DAY)
    val day3 = day2.plus(1, DateTimeUnit.DAY)

    tracker.recordReview(day1)
    tracker.recordReview(day2)
    tracker.recordReview(day3)

    tracker.streakDays(day3) shouldBe 3
  }

  @Test
  fun streakDaysAtMidnightBoundaryCarriesYesterdaysCountBeforeTodaysFirstReview() = runTest {
    val tracker = StreakTracker(InMemoryDailyActivityStore())
    val day2 = day1.plus(1, DateTimeUnit.DAY)

    tracker.recordReview(day1)

    // Midnight has rolled over into day2, but day2 has had no review yet: the streak is not yet
    // broken and today's own count resets.
    tracker.streakDays(day2) shouldBe 1
    tracker.cardsCompletedToday(day2) shouldBe 0
  }

  @Test
  fun streakDaysAtMidnightBoundaryContinuesOnTodaysFirstReview() = runTest {
    val tracker = StreakTracker(InMemoryDailyActivityStore())
    val day2 = day1.plus(1, DateTimeUnit.DAY)

    tracker.recordReview(day1)
    tracker.recordReview(day2)

    tracker.streakDays(day2) shouldBe 2
  }

  @Test
  fun multiDayGapResetsStreakToOne() = runTest {
    val tracker = StreakTracker(InMemoryDailyActivityStore())
    val day2 = day1.plus(1, DateTimeUnit.DAY)
    // day3 is skipped entirely: no review at all.
    val day4 = day1.plus(3, DateTimeUnit.DAY)

    tracker.recordReview(day1)
    tracker.recordReview(day2)
    tracker.recordReview(day4)

    tracker.streakDays(day4) shouldBe 1
  }

  @Test
  fun multiDayGapReadsAsBrokenBeforeTheNextReview() = runTest {
    val tracker = StreakTracker(InMemoryDailyActivityStore())
    val day2 = day1.plus(1, DateTimeUnit.DAY)
    val day3 = day1.plus(2, DateTimeUnit.DAY)

    tracker.recordReview(day1)
    // day2 has no review at all; by day3, the gap has broken the streak.

    tracker.streakDays(day3) shouldBe 0
  }

  @Test
  fun streakDaysHandlesALargeConsecutiveRun() = runTest {
    val tracker = StreakTracker(InMemoryDailyActivityStore())
    val streakSize = 400
    var lastDay = day1

    for (offset in 0 until streakSize) {
      lastDay = day1.plus(offset, DateTimeUnit.DAY)
      tracker.recordReview(lastDay)
    }

    tracker.streakDays(lastDay) shouldBe streakSize
    tracker.cardsCompletedToday(lastDay) shouldBe 1
  }
}
