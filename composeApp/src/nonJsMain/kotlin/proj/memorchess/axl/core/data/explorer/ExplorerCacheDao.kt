package proj.memorchess.axl.core.data.explorer

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** DAO for the explorer cache. */
@Dao
interface ExplorerCacheDao {

  /** Returns the cached entry for [key], or `null` when missing. */
  @Query("SELECT * FROM ExplorerCacheEntity WHERE `key` = :key")
  suspend fun get(key: String): ExplorerCacheEntity?

  /** Inserts or replaces a cache entry. */
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entry: ExplorerCacheEntity)

  /** Removes every cached entry. */
  @Query("DELETE FROM ExplorerCacheEntity") suspend fun eraseAll()
}
