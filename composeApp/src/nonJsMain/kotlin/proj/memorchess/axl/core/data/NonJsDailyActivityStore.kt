package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDate

/** Room backed [DailyActivityStore] for non-JS platforms. */
internal class NonJsDailyActivityStore(private val database: CustomDatabase) : DailyActivityStore {

  override suspend fun getRecord(date: LocalDate): DailyActivityRecord? =
    database.getDailyActivityDao().getRecord(date.toString())?.toRecord()

  override suspend fun putRecord(record: DailyActivityRecord) {
    database.getDailyActivityDao().putRecord(record.toEntity())
  }
}

private fun DailyActivityEntity.toRecord(): DailyActivityRecord =
  DailyActivityRecord(LocalDate.parse(date), cardsReviewed, isActive, streakLength)

private fun DailyActivityRecord.toEntity(): DailyActivityEntity =
  DailyActivityEntity(date.toString(), cardsReviewed, isActive, streakLength)

actual fun getPlatformSpecificDailyActivityStore(): DailyActivityStore {
  return NonJsDailyActivityStore(customDatabase)
}
