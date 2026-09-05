package proj.memorchess.axl.core.streak

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import proj.memorchess.axl.core.data.DailyActivityRecord
import proj.memorchess.axl.core.data.DailyActivityStore
import proj.memorchess.axl.core.date.DateUtil

/**
 * Local daily-activity and streak driver, the single chokepoint over [DailyActivityStore].
 *
 * A local day counts towards the streak once at least one review lands on it; a day that ends with
 * zero reviews breaks it. Rollover happens at local midnight, since every method keys off a
 * [LocalDate] rather than a wall clock instant. Cross-device sync is explicitly out of scope: this
 * reads and writes local storage only.
 */
class StreakTracker(private val store: DailyActivityStore) {

  /** Number of positions reviewed for the first time on [day]. */
  suspend fun cardsCompletedToday(day: LocalDate = DateUtil.today()): Int =
    store.getRecord(day)?.cardsReviewed ?: 0

  /**
   * Current streak length in days, as of [day].
   *
   * Once [day] itself has a review, its own streak length is returned. Otherwise the streak is not
   * yet broken by [day] alone: it still reflects the previous day's length when that day was
   * active, and falls back to `0` once a full day has passed with no review.
   */
  suspend fun streakDays(day: LocalDate = DateUtil.today()): Int {
    val today = store.getRecord(day)
    if (today?.isActive == true) return today.streakLength
    val yesterday = store.getRecord(day.minus(1, DateTimeUnit.DAY))
    return if (yesterday?.isActive == true) yesterday.streakLength else 0
  }

  /**
   * Records one reviewed position on [day]: increments its count and, on [day]'s first review,
   * chains its streak length onto the previous day's (`1` when that day was not active).
   */
  suspend fun recordReview(day: LocalDate = DateUtil.today()) {
    val existing = store.getRecord(day)
    val streakLength = existing?.streakLength ?: computeStreakLength(day)
    store.putRecord(
      DailyActivityRecord(
        date = day,
        cardsReviewed = (existing?.cardsReviewed ?: 0) + 1,
        isActive = true,
        streakLength = streakLength,
      )
    )
  }

  private suspend fun computeStreakLength(day: LocalDate): Int {
    val yesterday = store.getRecord(day.minus(1, DateTimeUnit.DAY))
    return if (yesterday?.isActive == true) yesterday.streakLength + 1 else 1
  }
}
