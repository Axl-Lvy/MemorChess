package proj.memorchess.axl.core.data

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate

/** Type converters for to handle kotlinx.datetime.LocalDate type. */
class DateConverters {
  /** Converts a LocalDate to a String for storage in the database. */
  @TypeConverter
  fun fromLocalDate(date: LocalDate?): String? {
    return date?.toString()
  }

  /** Converts a String from the database back to a LocalDate. */
  @TypeConverter
  fun toLocalDate(dateString: String?): LocalDate? {
    return dateString?.let { LocalDate.parse(it) }
  }
}
