package proj.memorchess.axl.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** DAO for the sync outbox. See [OutboxEntryEntity]. */
@Dao
interface OutboxDao {

  /** Queues [entry], replacing any existing row with the same key so repeat marks collapse. */
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entry: OutboxEntryEntity)

  /** Every currently queued entry. */
  @Query("SELECT * FROM OutboxEntryEntity") suspend fun getAll(): List<OutboxEntryEntity>

  /** Removes [entries] once their rows have been pushed. */
  @Delete suspend fun delete(entries: List<OutboxEntryEntity>)
}
