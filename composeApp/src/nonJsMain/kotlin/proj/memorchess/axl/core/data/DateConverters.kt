package proj.memorchess.axl.core.data

import androidx.room.TypeConverter
import kotlin.time.Instant
import kotlinx.datetime.*

/** Type converters for to handle kotlinx.datetime.LocalDate type. */
class DateConverters {
  /** Converts a LocalDate to a String for storage in the database. */
  @TypeConverter
  fun fromLocalDate(date: LocalDate?): Long? {
    return date?.toEpochDays()
  }

  /** Converts a String from the database back to a LocalDate. */
  @TypeConverter
  fun toLocalDate(epochDays: Long?): LocalDate? {
    return if (epochDays == null) null else LocalDate.fromEpochDays(epochDays)
  }

  /** Converts a LocalDateTime to a String for storage in the database. */
  @TypeConverter
  fun fromLocalTime(date: Instant?): Long? {
    return date?.epochSeconds
  }

  /** Converts a String from the database back to a LocalDateTime. */
  @TypeConverter
  fun toLocalTime(dateLong: Long?): Instant? {
    if (dateLong == null) return null
    return Instant.fromEpochSeconds(dateLong)
  }
}
