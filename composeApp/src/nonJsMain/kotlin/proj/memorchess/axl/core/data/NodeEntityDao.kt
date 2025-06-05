package proj.memorchess.axl.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface NodeEntityDao {

  suspend fun createNewNode(node: NodeWithMoves) {
    insertNode(node.node)
    insertMoves(node.nextMoves)
    insertMoves(node.previousMoves)
  }

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertNode(item: NodeEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMoves(items: Collection<MoveEntity>)

  @Query("DELETE FROM MoveEntity WHERE origin = :origin AND destination = :destination")
  suspend fun removeMove(origin: String, destination: String)

  @Transaction @Query("SELECT * FROM NodeEntity") suspend fun getAll(): List<NodeWithMoves>

  @Query("DELETE FROM NodeEntity WHERE fenRepresentation = :fen") suspend fun delete(fen: String)

  @Query(value = "DELETE FROM NodeEntity") suspend fun deleteAll()
}
