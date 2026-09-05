package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDate

/**
 * Local only persistence seam for [DailyActivityRecord], one row per local calendar date. Never
 * reaches the sync outbox.
 */
interface DailyActivityStore {

  /** Reads [date]'s record, or `null` when no review has ever been recorded for it. */
  suspend fun getRecord(date: LocalDate): DailyActivityRecord?

  /** Replaces [DailyActivityRecord.date]'s row wholesale. */
  suspend fun putRecord(record: DailyActivityRecord)

  /** Hard wipes every recorded date. */
  suspend fun eraseAll()
}

expect fun getPlatformSpecificDailyActivityStore(): DailyActivityStore
