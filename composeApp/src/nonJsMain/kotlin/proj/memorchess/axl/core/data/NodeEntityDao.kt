package proj.memorchess.axl.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlin.time.Instant

/** DAO for managing [NodeEntity] and [MoveEntity] (linked by [NodeWithMoves]). */
@Dao
interface NodeEntityDao {

  /**
   * Inserts a new node with its moves into the database.
   *
   * If the node already exists, it will be replaced. Same for the moves.
   *
   * @param nodes The node with its next and previous moves to insert.
   */
  @Transaction
  suspend fun insertNodeAndMoves(nodes: Collection<NodeWithMoves>) {
    nodes.forEach {
      insertNode(it.node)
      insertMoves(it.nextMoves + it.previousMoves)
    }
  }

  /**
   * Inserts a new node into the database.
   *
   * If the node already exists, it will be replaced.
   *
   * @param item The node to insert.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertNode(item: NodeEntity)

  /**
   * Inserts moves into the database.
   *
   * If a move already exists, it will be replaced.
   *
   * @param items The collection of moves to insert.
   */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMoves(items: Collection<MoveEntity>)

  /**
   * Soft deletes a move by flipping its `isDeleted` flag.
   *
   * @param origin The move's origin.
   * @param move The move's notation.
   */
  @Query(
    "UPDATE MoveEntity SET isDeleted = TRUE WHERE isDeleted IS FALSE AND origin = :origin AND move = :move"
  )
  suspend fun softDeleteMove(origin: String, move: String)

  /** Hard deletes a move row. */
  @Query("DELETE FROM MoveEntity WHERE origin = :origin AND move = :move")
  suspend fun hardDeleteMove(origin: String, move: String)

  /**
   * Soft deletes all moves leaving [origin] by flipping their `isDeleted` flag.
   *
   * @param origin The FEN string of the origin position.
   */
  @Query("UPDATE MoveEntity SET isDeleted = TRUE WHERE isDeleted IS FALSE AND origin = :origin")
  suspend fun softDeleteMoveFrom(origin: String)

  /** Hard deletes every move row leaving [origin]. */
  @Query("DELETE FROM MoveEntity WHERE origin = :origin")
  suspend fun hardDeleteMoveFrom(origin: String)

  /**
   * Soft deletes all moves arriving at [destination] by flipping their `isDeleted` flag.
   *
   * @param destination The FEN string of the destination position.
   */
  @Query(
    "UPDATE MoveEntity SET isDeleted = TRUE WHERE isDeleted IS FALSE AND destination = :destination"
  )
  suspend fun softDeleteMoveTo(destination: String)

  /** Hard deletes every move row arriving at [destination]. */
  @Query("DELETE FROM MoveEntity WHERE destination = :destination")
  suspend fun hardDeleteMoveTo(destination: String)

  @Transaction
  @Query("SELECT * FROM NodeEntity WHERE positionKey = :fen AND isDeleted IS FALSE")
  suspend fun getNode(fen: String): NodeWithMoves?

  /** Soft deletes a node row by flipping its `isDeleted` flag. */
  @Query("UPDATE NodeEntity SET isDeleted = TRUE WHERE isDeleted IS FALSE AND positionKey = :fen")
  suspend fun softDeleteNode(fen: String)

  /** Hard deletes a node row. */
  @Query("DELETE FROM NodeEntity WHERE positionKey = :fen") suspend fun hardDeleteNode(fen: String)

  /** Hard wipes every node row. */
  @Query("DELETE FROM NodeEntity") suspend fun eraseAllNodes()

  /** Hard wipes every move row. */
  @Query("DELETE FROM MoveEntity") suspend fun eraseAllMoves()

  /**
   * Retrieves all nodes with their moves from the database.
   *
   * @return A list of [NodeWithMoves] containing nodes and their associated moves.
   */
  @Transaction @Query("SELECT * FROM NodeEntity") suspend fun getAllNodes(): List<NodeWithMoves>

  @Query("SELECT * FROM MoveEntity WHERE isDeleted IS FALSE")
  suspend fun getAllMoves(): List<MoveEntity>

  @Query("SELECT * FROM MoveEntity") suspend fun getAllMovesWithDeletedOnes(): List<MoveEntity>

  @Query("SELECT MAX(updatedAt) FROM NodeEntity") suspend fun getLastNodeUpdate(): Instant?

  @Query("SELECT * FROM NodeEntity") suspend fun getPositions(): List<NodeEntity>

  @Query("SELECT MAX(updatedAt) FROM MoveEntity") suspend fun getLastMoveUpdate(): Instant?
}
