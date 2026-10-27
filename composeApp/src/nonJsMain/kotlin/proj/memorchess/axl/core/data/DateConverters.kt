package proj.memorchess.axl.core.data

import androidx.room.TypeConverter
import kotlin.time.Instant
import kotlinx.datetime.*

/** Room type converters for [LocalDate] and [Instant]. */
class DateConverters {
  /** Stores a [LocalDate] as epoch days. */
  @TypeConverter
  fun fromLocalDate(date: LocalDate?): Long? {
    return date?.toEpochDays()
  }

  /** Reads a [LocalDate] from epoch days. */
  @TypeConverter
  fun toLocalDate(epochDays: Long?): LocalDate? {
    return if (epochDays == null) null else LocalDate.fromEpochDays(epochDays)
  }

  /** Stores an [Instant] as epoch seconds, dropping sub-second precision. */
  @TypeConverter
  fun fromLocalTime(date: Instant?): Long? {
    return date?.epochSeconds
  }

  /** Reads an [Instant] from epoch seconds. */
  @TypeConverter
  fun toLocalTime(dateLong: Long?): Instant? {
    if (dateLong == null) return null
    return Instant.fromEpochSeconds(dateLong)
  }
}
