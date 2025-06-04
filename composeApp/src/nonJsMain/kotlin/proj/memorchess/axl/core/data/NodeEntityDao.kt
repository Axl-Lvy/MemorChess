package proj.memorchess.axl.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NodeEntityDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: NodeEntity)

  @Query("SELECT * FROM NodeEntity") suspend fun getAll(): List<NodeEntity>

  @Query("DELETE FROM NodeEntity WHERE fenRepresentation = :fen") suspend fun delete(fen: String)

  @Query(value = "DELETE FROM NodeEntity") suspend fun deleteAll()
}
