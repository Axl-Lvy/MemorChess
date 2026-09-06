package proj.memorchess.axl.core.streak

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
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
  fun eraseAllClearsTheStreakAndTodaysCount() = runTest {
    val tracker = StreakTracker(InMemoryDailyActivityStore())
    val day2 = day1.plus(1, DateTimeUnit.DAY)
    tracker.recordReview(day1)
    tracker.recordReview(day2)

    tracker.eraseAll()

    tracker.cardsCompletedToday(day2) shouldBe 0
    tracker.streakDays(day2) shouldBe 0
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

  // WEEK ACTIVITY

  @Test
  fun weekActivityMarksOnlyTheActiveMidWeekDay() = runTest {
    val tracker = StreakTracker(InMemoryDailyActivityStore())
    // 2026-09-02 is a Wednesday.
    val wednesday = LocalDate(2026, 9, 2)
    tracker.recordReview(wednesday)

    tracker.weekActivity(wednesday) shouldBe listOf(false, false, true, false, false, false, false)
  }

  @Test
  fun weekActivityAnchoredOnMondayStartsTheWeekAtItself() = runTest {
    val tracker = StreakTracker(InMemoryDailyActivityStore())
    // 2026-08-31 is a Monday.
    val monday = LocalDate(2026, 8, 31)
    tracker.recordReview(monday)

    tracker.weekActivity(monday) shouldBe listOf(true, false, false, false, false, false, false)
  }

  @Test
  fun weekActivityAnchoredOnSundayEndsTheWeekAtItself() = runTest {
    val tracker = StreakTracker(InMemoryDailyActivityStore())
    // 2026-09-06 is a Sunday.
    val sunday = LocalDate(2026, 9, 6)
    tracker.recordReview(sunday)

    tracker.weekActivity(sunday) shouldBe listOf(false, false, false, false, false, false, true)
  }

  @Test
  fun weekActivityIsAllFalseForAnEmptyStore() = runTest {
    val tracker = StreakTracker(InMemoryDailyActivityStore())

    tracker.weekActivity(LocalDate(2026, 9, 2)) shouldBe List(7) { false }
  }

  @Test
  fun weekActivityIsAllTrueWhenEveryDayInTheWeekIsActive() = runTest {
    val tracker = StreakTracker(InMemoryDailyActivityStore())
    val monday = LocalDate(2026, 8, 31)
    for (offset in 0..6) {
      tracker.recordReview(monday.plus(offset, DateTimeUnit.DAY))
    }

    tracker.weekActivity(LocalDate(2026, 9, 2)) shouldBe List(7) { true }
  }

  @Test
  fun weekActivityDoesNotBleedIntoTheAdjacentWeek() = runTest {
    val tracker = StreakTracker(InMemoryDailyActivityStore())
    val wednesday = LocalDate(2026, 9, 2)
    // The Monday before and the Monday after this ISO week: both out of range.
    tracker.recordReview(wednesday.minus(7, DateTimeUnit.DAY))
    tracker.recordReview(wednesday.plus(7, DateTimeUnit.DAY))

    tracker.weekActivity(wednesday) shouldBe List(7) { false }
  }
}
