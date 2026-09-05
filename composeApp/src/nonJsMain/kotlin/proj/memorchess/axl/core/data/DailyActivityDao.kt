package proj.memorchess.axl.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** DAO for [DailyActivityEntity]. */
@Dao
interface DailyActivityDao {

  /** Reads the row for [date], or `null` when none exists yet. */
  @Query("SELECT * FROM DailyActivityEntity WHERE date = :date")
  suspend fun getRecord(date: String): DailyActivityEntity?

  /** Replaces [entity]'s row wholesale. */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun putRecord(entity: DailyActivityEntity)
}
