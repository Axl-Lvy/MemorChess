package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil

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
  suspend fun deletePosition(position: PositionIdentifier, updatedAt: Instant = DateUtil.now())

  /**
   * Deletes a node
   *
   * @param origin The origin of the move
   * @param move The name of move to delete
   * @param updatedAt The timestamp to record for this deletion, defaults to now.
   */
  suspend fun deleteMove(origin: PositionIdentifier, move: String, updatedAt: Instant = DateUtil.now())

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
