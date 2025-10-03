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
   * Remove a move from the database.
   *
   * If the move already exists, it will be replaced.
   *
   * @param origin The move's origin.
   * @param move The move's notation.
   */
  @Query(
    "UPDATE MoveEntity SET isDeleted = TRUE WHERE isDeleted IS FALSE AND origin = :origin AND move = :move"
  )
  suspend fun removeMove(origin: String, move: String)

  /**
   * Remove all moves from a specific origin in the database.
   *
   * This will delete all moves that originate from the specified position.
   *
   * @param origin The FEN string of the origin position.
   */
  @Query("UPDATE MoveEntity SET isDeleted = TRUE WHERE isDeleted IS FALSE AND origin = :origin")
  suspend fun removeMoveFrom(origin: String)

  /**
   * Remove all moves to a specific destination in the database.
   *
   * This will delete all moves that lead to the specified position.
   *
   * @param destination The FEN string of the destination position.
   */
  @Query(
    "UPDATE MoveEntity SET isDeleted = TRUE WHERE isDeleted IS FALSE AND destination = :destination"
  )
  suspend fun removeMoveTo(destination: String)

  @Transaction
  @Query("SELECT * FROM NodeEntity WHERE fenRepresentation = :fen AND isDeleted IS FALSE")
  suspend fun getNode(fen: String): NodeWithMoves?

  /**
   * Retrieves a specific node with its moves by FEN representation.
   *
   * @param fen The FEN representation of the node to retrieve.
   * @return A [NodeWithMoves] containing the node and its associated moves, or null if not found.
   */
  @Query(
    "UPDATE NodeEntity SET isDeleted = TRUE WHERE isDeleted IS FALSE AND fenRepresentation = :fen"
  )
  suspend fun delete(fen: String)

  /** Marks all nodes as deleted. */
  @Query(value = "UPDATE NodeEntity SET isDeleted = TRUE") suspend fun deleteAllNodes()

  /** Delete all nodes that were updated after a specific date. */
  @Query(value = "DELETE FROM NodeEntity WHERE updatedAt >= :date")
  suspend fun deleteNewerNodes(date: Instant)

  /** Marks all moves as deleted. */
  @Query(value = "UPDATE MoveEntity SET isDeleted = TRUE") suspend fun deleteAllMoves()

  /** Delete all moves that were updated after a specific date. */
  @Query(value = "DELETE FROM MoveEntity WHERE updatedAt >= :date")
  suspend fun deleteNewerMoves(date: Instant)

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
