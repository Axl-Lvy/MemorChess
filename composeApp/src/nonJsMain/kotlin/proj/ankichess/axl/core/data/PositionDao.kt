package proj.ankichess.axl.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PositionDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(item: NodeEntity)

  @Query("SELECT * FROM PositionEntity") suspend fun getAll(): List<NodeEntity>

  @Query("DELETE FROM PositionEntity WHERE fenRepresentation = :fen")
  suspend fun delete(fen: String)
}
