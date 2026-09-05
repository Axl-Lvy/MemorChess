package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDate

/**
 * Low level persistence seam for [DailyActivityRecord], one row per local calendar date.
 *
 * Deliberately dumb: it neither knows what "active" means nor computes a streak. Only
 * [proj.memorchess.axl.core.streak.StreakTracker] is expected to read and write through this seam;
 * it is the single chokepoint that turns a raw record into streak semantics, mirroring how
 * [proj.memorchess.axl.core.graph.TreeStore] is the chokepoint over [DatabaseQueryManager]. Local
 * only: unlike [DatabaseQueryManager], nothing here ever reaches the sync outbox.
 */
interface DailyActivityStore {

  /** Reads [date]'s record, or `null` when no review has ever been recorded for it. */
  suspend fun getRecord(date: LocalDate): DailyActivityRecord?

  /** Replaces [DailyActivityRecord.date]'s row wholesale. */
  suspend fun putRecord(record: DailyActivityRecord)
}

expect fun getPlatformSpecificDailyActivityStore(): DailyActivityStore
