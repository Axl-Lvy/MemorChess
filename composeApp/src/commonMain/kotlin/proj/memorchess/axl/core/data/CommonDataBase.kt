package proj.memorchess.axl.core.data

/** Interface for the application's database operations on positions. */
interface ICommonDatabase {
  /**
   * Retrieves all stored positions.
   *
   * @return A list of all stored positions as [IStoredNode] objects.
   */
  suspend fun getAllPositions(): List<IStoredNode>

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

  /** Deletes all stored positions. */
  suspend fun deleteAllPositions()

  /**
   * Inserts a new position.
   *
   * @param position The [IStoredNode] to insert.
   */
  suspend fun insertPosition(position: IStoredNode)
}

/**
 * Get the default database.
 *
 * **Always prefer using [DatabaseHolder.getDatabase] to get the database.**
 *
 * @return The default database.
 */
expect fun getCommonDatabase(): ICommonDatabase
