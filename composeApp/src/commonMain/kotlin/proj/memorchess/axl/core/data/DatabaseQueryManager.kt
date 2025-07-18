package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDateTime

/** Interface for the application's database operations on positions. */
interface DatabaseQueryManager {

  /**
   * Retrieves all stored positions.
   *
   * @return A list of all stored positions as [StoredNode] objects.
   */
  suspend fun getAllNodes(): List<StoredNode>

  /**
   * Retrieves all stored positions without any move.
   *
   * @return A list of all stored positions as [UnlinkedStoredNode] objects.
   */
  suspend fun getAllPositions(): List<UnlinkedStoredNode>

  /** Retrieves a specific position. */
  suspend fun getPosition(positionIdentifier: PositionIdentifier): StoredNode?

  /**
   * Deletes a specific position by its FEN.
   *
   * @param fen The FEN identifying the position to delete.
   */
  suspend fun deletePosition(fen: String)

  /**
   * Deletes a node
   *
   * @param origin The origin of the move
   * @param move The name of move to delete
   */
  suspend fun deleteMove(origin: String, move: String)

  /**
   * Inserts a new move.
   *
   * @param move The [StoredMove] to insert.
   */
  suspend fun insertMove(move: StoredMove)

  /**
   * Get all moves
   *
   * @param withDeletedOnes Whether to include deleted moves or not
   */
  suspend fun getAllMoves(withDeletedOnes: Boolean = false): List<StoredMove>

  /** Deletes all positions and moves. */
  suspend fun deleteAll()

  /**
   * Inserts a new position.
   *
   * @param position The [StoredNode] to insert.
   */
  suspend fun insertPosition(position: StoredNode)

  /** Retrieves the last move update time. */
  suspend fun getLastMoveUpdate(): LocalDateTime?

  /** Checks if the database is can be used. */
  fun isActive(): Boolean
}
