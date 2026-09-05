package proj.memorchess.axl.core.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/** DAO for the sync outbox. See [OutboxEntryEntity]. */
@Dao
interface OutboxDao {

  /**
   * Queues one entry, keeping the higher of the stored and new `deviceSeq` on a repeat mark. See
   * [DatabaseQueryManager.markDirty].
   */
  @Query(
    "INSERT INTO OutboxEntryEntity (kind, key1, key2, key3, deviceSeq) " +
      "VALUES (:kind, :key1, :key2, :key3, :deviceSeq) " +
      "ON CONFLICT(kind, key1, key2, key3) DO UPDATE SET deviceSeq = MAX(deviceSeq, excluded.deviceSeq)"
  )
  suspend fun upsert(kind: String, key1: String, key2: String = "", key3: String = "", deviceSeq: Long)

  /** Every currently queued entry, ordered ascending by `deviceSeq`. */
  @Query("SELECT * FROM OutboxEntryEntity ORDER BY deviceSeq ASC")
  suspend fun getAll(): List<OutboxEntryEntity>

  /**
   * Removes the entry keyed by [kind]/[key1]/[key2]/[key3], but only when its queued `deviceSeq`
   * has not moved past [pushedSeq]: a mark that landed after the row was read for push survives.
   * See [DatabaseQueryManager.clearDirty].
   */
  @Query(
    "DELETE FROM OutboxEntryEntity WHERE kind = :kind AND key1 = :key1 AND key2 = :key2 " +
      "AND key3 = :key3 AND deviceSeq <= :pushedSeq"
  )
  suspend fun deleteIfNotNewer(kind: String, key1: String, key2: String, key3: String, pushedSeq: Long)

  /** Applies [deleteIfNotNewer] to every entry in [entries], in one transaction. */
  @Transaction
  suspend fun clearIfNotNewer(entries: Collection<OutboxEntryEntity>) {
    entries.forEach { deleteIfNotNewer(it.kind, it.key1, it.key2, it.key3, it.deviceSeq) }
  }

  /** Hard wipes every outbox entry. Used by [DatabaseQueryManager.eraseAll]. */
  @Query("DELETE FROM OutboxEntryEntity") suspend fun eraseAll()
}
