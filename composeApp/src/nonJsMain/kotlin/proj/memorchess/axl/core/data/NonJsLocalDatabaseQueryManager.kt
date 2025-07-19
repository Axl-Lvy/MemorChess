package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDateTime

/** Database for non-JS platforms */
object NonJsLocalDatabaseQueryManager : DatabaseQueryManager {

  private val database = getRoomDatabase(databaseBuilder())

  override suspend fun getAllNodes(): List<StoredNode> {
    return database.getNodeEntityDao().getAllNodes().map { it.toStoredNode() }
  }

  override suspend fun getAllPositions(): List<UnlinkedStoredNode> {
    return database.getNodeEntityDao().getPositions().map { it.toUnlinkedStoredNode() }
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): StoredNode? {
    return database.getNodeEntityDao().getNode(positionIdentifier.fenRepresentation)?.toStoredNode()
  }

  override suspend fun deletePosition(fen: String) {
    database.getNodeEntityDao().delete(fen)
    database.getNodeEntityDao().removeMoveFrom(fen)
  }

  override suspend fun deleteMove(origin: String, move: String) {
    database.getNodeEntityDao().removeMove(origin, move)
  }

  override suspend fun insertMove(move: StoredMove) {
    database.getNodeEntityDao().insertMoves(listOf(MoveEntity.convertToEntity(move)))
  }

  override suspend fun getAllMoves(withDeletedOnes: Boolean): List<StoredMove> {
    return (if (withDeletedOnes) database.getNodeEntityDao().getAllMovesWithDeletedOnes()
      else database.getNodeEntityDao().getAllMoves())
      .map { it.toStoredMove() }
  }

  override suspend fun deleteAll() {
    database.getNodeEntityDao().deleteAllNodes()
    database.getNodeEntityDao().deleteAllMoves()
  }

  override suspend fun insertPosition(position: StoredNode) {
    database.getNodeEntityDao().insertNodeAndMoves(NodeWithMoves.convertToEntity(position))
  }

  override suspend fun getLastMoveUpdate(): LocalDateTime? {
    return database.getNodeEntityDao().getLastMoveUpdate()
  }

  override fun isActive(): Boolean {
    return true
  }
}

actual fun getLocalDatabase(): DatabaseQueryManager {
  return NonJsLocalDatabaseQueryManager
}
