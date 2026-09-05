package proj.memorchess.axl.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a [DailyActivityRecord] ready to be stored in the database.
 *
 * @property date Local date this row covers, in ISO-8601 form (e.g. `"2026-09-05"`).
 * @property cardsReviewed Number of distinct positions reviewed for the first time on [date].
 * @property isActive Whether [date] counts towards the streak.
 * @property streakLength Consecutive active days ending on [date], inclusive.
 */
@Entity(tableName = "DailyActivityEntity")
data class DailyActivityEntity(
  @PrimaryKey(autoGenerate = false) val date: String,
  val cardsReviewed: Int,
  val isActive: Boolean,
  val streakLength: Int,
)
