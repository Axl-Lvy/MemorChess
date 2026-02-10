package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil

/** Interface for the application's database operations on positions and moves. */
interface DatabaseQueryManager {

  /**
   * Retrieves all stored moves.
   *
   * @return A list of all stored moves as [DataMove] objects.
   */
  suspend fun getAllMoves(withDeletedOnes: Boolean = false): List<DataMove>

  /**
   * Retrieves all stored positions.
   *
   * @return A list of all stored positions as [DataPosition] objects.
   */
  suspend fun getAllPositions(withDeletedOnes: Boolean = false): List<DataPosition>

  /** Retrieves a specific position's metadata. */
  suspend fun getPosition(positionIdentifier: PositionIdentifier): DataPosition?

  /** Retrieves all moves originating from or leading to a specific position. */
  suspend fun getMovesForPosition(positionIdentifier: PositionIdentifier): List<DataMove>

  /**
   * Deletes a specific position by its FEN.
   *
   * @param position The position to delete.
   */
  suspend fun deletePosition(position: PositionIdentifier, updatedAt: Instant = DateUtil.now())

  /**
   * Deletes a move.
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
   * Inserts moves and their associated position metadata.
   *
   * @param moves The [DataMove] objects to insert.
   * @param positions The [DataPosition] metadata for positions referenced by the moves.
   */
  suspend fun insertMoves(moves: List<DataMove>, positions: List<DataPosition>)

  /** Retrieves the last move update time. */
  suspend fun getLastUpdate(): Instant?

  /** Checks if the database is can be used. */
  fun isActive(): Boolean
}
