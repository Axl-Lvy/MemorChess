package proj.memorchess.axl.core.streak

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import proj.memorchess.axl.core.data.DailyActivityRecord
import proj.memorchess.axl.core.data.DailyActivityStore
import proj.memorchess.axl.core.date.DateUtil

/**
 * Tracks local daily activity and the review streak. A local day counts once it has a review; a day
 * that ends with none breaks the streak.
 */
class StreakTracker(private val store: DailyActivityStore) {

  /** Number of positions reviewed for the first time on [day]. */
  suspend fun cardsCompletedToday(day: LocalDate = DateUtil.today()): Int =
    store.getRecord(day)?.cardsReviewed ?: 0

  /**
   * Current streak length in days as of [day]. A [day] without a review yet still shows the
   * previous day's streak.
   */
  suspend fun streakDays(day: LocalDate = DateUtil.today()): Int {
    val today = store.getRecord(day)
    if (today?.isActive == true) return today.streakLength
    val yesterday = store.getRecord(day.minus(1, DateTimeUnit.DAY))
    return if (yesterday?.isActive == true) yesterday.streakLength else 0
  }

  /** Records one reviewed position on [day], extending the previous day's streak if any. */
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

  /**
   * Whether each day of the ISO week (Monday to Sunday) containing [day] counts towards the streak,
   * oldest first.
   */
  suspend fun weekActivity(day: LocalDate = DateUtil.today()): List<Boolean> {
    val monday = day.minus(day.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
    return (0..6).map { offset ->
      store.getRecord(monday.plus(offset, DateTimeUnit.DAY))?.isActive == true
    }
  }

  /** Wipes every recorded date, resetting the streak and today's count to zero. */
  suspend fun eraseAll() {
    store.eraseAll()
  }

  private suspend fun computeStreakLength(day: LocalDate): Int {
    val yesterday = store.getRecord(day.minus(1, DateTimeUnit.DAY))
    return if (yesterday?.isActive == true) yesterday.streakLength + 1 else 1
  }
}
