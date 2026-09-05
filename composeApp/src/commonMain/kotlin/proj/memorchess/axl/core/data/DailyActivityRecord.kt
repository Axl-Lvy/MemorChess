package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDate

/**
 * Persisted review activity for one local calendar date.
 *
 * @property date Local date this record covers.
 * @property cardsReviewed Number of distinct positions reviewed for the first time on [date].
 * @property isActive Whether [date] counts towards the streak. Currently always `cardsReviewed >
 *   0`, kept as its own field so a future exemption (e.g. a frozen day) would not need a schema
 *   change.
 * @property streakLength Consecutive active days ending on [date], inclusive. `1` when [date] is
 *   the first day of a new streak, `0` when [isActive] is `false`.
 */
data class DailyActivityRecord(
  val date: LocalDate,
  val cardsReviewed: Int,
  val isActive: Boolean,
  val streakLength: Int,
)
