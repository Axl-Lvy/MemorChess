package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDateTime

/** Interface for the application's database operations on positions. */
interface ILocalDatabase {
  /**
   * Retrieves all stored positions.
   *
   * @return A list of all stored positions as [StoredNode] objects.
   */
  suspend fun getAllPositions(): List<StoredNode>

  suspend fun getPosition(positionIdentifier: PositionIdentifier): StoredNode?

  /**
   * Deletes a specific position by its FEN.
   *
   * @param fen The FEN identifying the position to delete.
   */
  suspend fun deletePosition(fen: String)

  suspend fun deleteMoveFrom(origin: String)

  suspend fun deleteMoveTo(destination: String)

  suspend fun deleteMove(origin: String, move: String)

  suspend fun insertMove(move: StoredMove)

  suspend fun getAllMoves(): List<StoredMove>

  /** Deletes all stored moves. */
  suspend fun deleteAllMoves()

  /** Deletes all stored positions and their associated moves. */
  suspend fun deleteAllNodes()

  /**
   * Inserts a new position.
   *
   * @param position The [StoredNode] to insert.
   */
  suspend fun insertPosition(position: StoredNode)

  suspend fun getLastMoveUpdate(): LocalDateTime?

  suspend fun getMovesUpdatedAfter(date: LocalDateTime): List<StoredMove>

  suspend fun getLastNodeUpdate(): LocalDateTime?

  suspend fun getNodesUpdatedAfter(date: LocalDateTime): List<UnlinkedStoredNode>
}

/**
 * Get the default database.
 *
 * **Always prefer using [DatabaseHolder.getDatabase] to get the database.**
 *
 * @return The default database.
 */
expect fun getCommonDatabase(): ILocalDatabase
