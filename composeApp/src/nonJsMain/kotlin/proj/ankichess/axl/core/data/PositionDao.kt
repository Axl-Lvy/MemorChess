package proj.ankichess.axl.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PositionDao {

  @Insert suspend fun insert(item: PositionEntity)

  @Query("SELECT * FROM PositionEntity") fun getByFen(): Flow<PositionEntity>

  @Query("DELETE FROM yourDatabaseTable WHERE fenRepresentation = :fen") fun delete(fen: String)
}
