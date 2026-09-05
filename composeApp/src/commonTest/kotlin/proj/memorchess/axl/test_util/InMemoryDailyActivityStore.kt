package proj.memorchess.axl.test_util

import kotlinx.datetime.LocalDate
import proj.memorchess.axl.core.data.DailyActivityRecord
import proj.memorchess.axl.core.data.DailyActivityStore

/** Test only in memory [DailyActivityStore]. Not used by production code. */
internal class InMemoryDailyActivityStore : DailyActivityStore {
  private val records = mutableMapOf<LocalDate, DailyActivityRecord>()

  override suspend fun getRecord(date: LocalDate): DailyActivityRecord? = records[date]

  override suspend fun putRecord(record: DailyActivityRecord) {
    records[record.date] = record
  }
}
