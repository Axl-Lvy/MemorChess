package proj.memorchess.axl.core.data

import kotlin.time.Instant

/** Interface for the application's database operations on positions. */
interface DatabaseQueryManager {

  /**
   * Retrieves all stored positions.
   *
   * @return A list of all stored positions as [DataNode] objects.
   */
  suspend fun getAllNodes(withDeletedOnes: Boolean = false): List<DataNode>

  /** Retrieves a specific position. */
  suspend fun getPosition(positionIdentifier: PositionIdentifier): DataNode?

  /**
   * Deletes a specific position by its FEN.
   *
   * @param position The position to delete.
   */
  suspend fun deletePosition(position: PositionIdentifier)

  /**
   * Deletes a node
   *
   * @param origin The origin of the move
   * @param move The name of move to delete
   */
  suspend fun deleteMove(origin: PositionIdentifier, move: String)

  /**
   * Deletes all positions and moves.
   *
   * @param hardFrom Everything is not entirely deleted, but marked as deleted. Except for entities
   *   that where updated after this date.
   */
  suspend fun deleteAll(hardFrom: Instant?)

  /**
   * Inserts a new position.
   *
   * @param positions The [DataNode] to insert.
   */
  suspend fun insertNodes(vararg positions: DataNode)

  /** Retrieves the last move update time. */
  suspend fun getLastUpdate(): Instant?

  /** Checks if the database is can be used. */
  fun isActive(): Boolean
}
