package proj.ankichess.axl.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PositionDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(item: PositionEntity)

  @Query("SELECT * FROM PositionEntity") fun getAll(): Flow<PositionEntity>

  @Query("DELETE FROM PositionEntity WHERE fenRepresentation = :fen")
  suspend fun delete(fen: String)
}
