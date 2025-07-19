package proj.memorchess.axl.core.data

import androidx.room.TypeConverter
import kotlinx.datetime.*
import kotlinx.datetime.toInstant

private const val numberOfSecondsInOneDay = 3600 * 24

/** Type converters for to handle kotlinx.datetime.LocalDate type. */
class DateConverters {
  /** Converts a LocalDate to a String for storage in the database. */
  @TypeConverter
  fun fromLocalDate(date: LocalDate?): Int? {
    return date?.toEpochDays()
  }

  /** Converts a String from the database back to a LocalDate. */
  @TypeConverter
  fun toLocalDate(epochDays: Int?): LocalDate? {
    return if (epochDays == null) null else LocalDate.fromEpochDays(epochDays)
  }

  /** Converts a LocalDateTime to a String for storage in the database. */
  @TypeConverter
  fun fromLocalTime(date: LocalDateTime?): Long? {
    return date?.toInstant(TimeZone.UTC)?.epochSeconds
  }

  /** Converts a String from the database back to a LocalDateTime. */
  @TypeConverter
  fun toLocalTime(dateLong: Long?): LocalDateTime? {
    if (dateLong == null) return null
    return LocalDateTime(
      LocalDate.fromEpochDays((dateLong / numberOfSecondsInOneDay).toInt()),
      LocalTime.fromSecondOfDay((dateLong % numberOfSecondsInOneDay).toInt()),
    )
  }
}
