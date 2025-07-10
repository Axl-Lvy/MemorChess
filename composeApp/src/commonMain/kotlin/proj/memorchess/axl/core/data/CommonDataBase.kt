package proj.memorchess.axl.core.data

/** Interface for the application's database operations on positions. */
interface ICommonDatabase {
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
}

/**
 * Get the default database.
 *
 * **Always prefer using [DatabaseHolder.getDatabase] to get the database.**
 *
 * @return The default database.
 */
expect fun getCommonDatabase(): ICommonDatabase
