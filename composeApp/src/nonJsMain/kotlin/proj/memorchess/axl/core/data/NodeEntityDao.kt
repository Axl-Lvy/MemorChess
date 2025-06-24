package proj.memorchess.axl.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** DAO for managing [NodeEntity] and [MoveEntity] (linked by [NodeWithMoves]). */
@Dao
interface NodeEntityDao {

  /**
   * Inserts a new node with its moves into the database.
   *
   * If the node already exists, it will be replaced. Same for the moves.
   *
   * @param node The node with its next and previous moves to insert.
   */
  @Transaction
  suspend fun insertNodeAndMoves(node: NodeWithMoves) {
    insertNode(node.node)
    insertMoves(node.nextMoves)
    insertMoves(node.previousMoves)
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
  @Query("DELETE FROM MoveEntity WHERE origin = :origin AND move = :move")
  suspend fun removeMove(origin: String, move: String)

  /**
   * Remove all moves from a specific origin in the database.
   *
   * This will delete all moves that originate from the specified position.
   *
   * @param origin The FEN string of the origin position.
   */
  @Query("DELETE FROM MoveEntity WHERE origin = :origin") suspend fun removeMoveFrom(origin: String)

  /**
   * Remove all moves to a specific destination in the database.
   *
   * This will delete all moves that lead to the specified position.
   *
   * @param destination The FEN string of the destination position.
   */
  @Query("DELETE FROM MoveEntity WHERE destination = :destination")
  suspend fun removeMoveTo(destination: String)

  /**
   * Retrieves all nodes with their moves from the database.
   *
   * @return A list of [NodeWithMoves] containing nodes and their associated moves.
   */
  @Transaction @Query("SELECT * FROM NodeEntity") suspend fun getAllNodes(): List<NodeWithMoves>

  @Transaction
  @Query("SELECT * FROM NodeEntity WHERE fenRepresentation = :fen")
  suspend fun getNode(fen: String): NodeWithMoves?

  /**
   * Retrieves a specific node with its moves by FEN representation.
   *
   * @param fen The FEN representation of the node to retrieve.
   * @return A [NodeWithMoves] containing the node and its associated moves, or null if not found.
   */
  @Query("DELETE FROM NodeEntity WHERE fenRepresentation = :fen") suspend fun delete(fen: String)

  /**
   * Deletes all nodes and their associated moves from the database.
   *
   * This operation will remove all entries in the NodeEntity and MoveEntity tables.
   */
  @Query(value = "DELETE FROM NodeEntity") suspend fun deleteAll()

  /** Delete all moves from the database. */
  @Query(value = "DELETE FROM MoveEntity") suspend fun deleteAllMoves()

  @Query("SELECT * FROM MoveEntity") fun getAllMoves(): List<MoveEntity>
}
