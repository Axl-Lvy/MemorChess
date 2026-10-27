package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDate

/**
 * Persisted review activity for one local calendar date.
 *
 * @property cardsReviewed Number of distinct positions reviewed for the first time on [date].
 * @property isActive Whether [date] counts towards the streak.
 * @property streakLength Consecutive active days ending on [date], inclusive; `0` when [isActive]
 *   is `false`.
 */
data class DailyActivityRecord(
  val date: LocalDate,
  val cardsReviewed: Int,
  val isActive: Boolean,
  val streakLength: Int,
)
