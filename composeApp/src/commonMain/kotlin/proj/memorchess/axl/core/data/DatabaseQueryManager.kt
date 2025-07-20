package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDateTime

/** Interface for the application's database operations on positions. */
interface DatabaseQueryManager {

  /**
   * Retrieves all stored positions.
   *
   * @return A list of all stored positions as [StoredNode] objects.
   */
  suspend fun getAllNodes(withDeletedOnes: Boolean = false): List<StoredNode>

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
   * Get all moves
   *
   * @param withDeletedOnes Whether to include deleted moves or not
   */
  suspend fun getAllMoves(withDeletedOnes: Boolean = false): List<StoredMove>

  /**
   * Deletes all positions and moves.
   *
   * @param hardFrom Everything is not entirely deleted, but marked as deleted. Except for entities
   *   that where updated after this date.
   */
  suspend fun deleteAll(hardFrom: LocalDateTime?)

  /**
   * Inserts a new position.
   *
   * @param positions The [StoredNode] to insert.
   */
  suspend fun insertNodes(vararg positions: StoredNode)

  /** Retrieves the last move update time. */
  suspend fun getLastUpdate(): LocalDateTime?

  /** Checks if the database is can be used. */
  fun isActive(): Boolean
}
