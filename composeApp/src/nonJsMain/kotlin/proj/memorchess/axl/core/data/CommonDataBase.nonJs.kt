package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDateTime

/** Database for non-JS platforms */
object NonJsLocalDatabase : ILocalDatabase {

  private val database = getRoomDatabase(databaseBuilder())

  override suspend fun getAllPositions(): List<StoredNode> {
    return database.getNodeEntityDao().getAllNodes().map { it.toStoredNode() }
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): StoredNode? {
    return database.getNodeEntityDao().getNode(positionIdentifier.fenRepresentation)?.toStoredNode()
  }

  override suspend fun deletePosition(fen: String) {
    database.getNodeEntityDao().delete(fen)
  }

  override suspend fun deleteMoveFrom(origin: String) {
    database.getNodeEntityDao().removeMoveFrom(origin)
  }

  override suspend fun deleteMoveTo(destination: String) {
    database.getNodeEntityDao().removeMoveTo(destination)
  }

  override suspend fun deleteMove(origin: String, move: String) {
    database.getNodeEntityDao().removeMove(origin, move)
  }

  override suspend fun insertMove(move: StoredMove) {
    database.getNodeEntityDao().insertMoves(listOf(MoveEntity.convertToEntity(move)))
  }

  override suspend fun getAllMoves(): List<StoredMove> {
    return database.getNodeEntityDao().getAllMoves().map { it.toStoredMove() }
  }

  override suspend fun deleteAllMoves() {
    database.getNodeEntityDao().deleteAllMoves()
  }

  override suspend fun deleteAllNodes() {
    database.getNodeEntityDao().deleteAllNodes()
  }

  override suspend fun insertPosition(position: StoredNode) {
    database.getNodeEntityDao().insertNodeAndMoves(NodeWithMoves.convertToEntity(position))
  }

  override suspend fun getLastMoveUpdate(): LocalDateTime? {
    return database.getNodeEntityDao().getLastMoveUpdate()
  }

  override suspend fun getMovesUpdatedAfter(date: LocalDateTime): List<StoredMove> {
    return database.getNodeEntityDao().getMovesUpdatedAfter(date).map { it.toStoredMove() }
  }

  override suspend fun getLastNodeUpdate(): LocalDateTime? {
    return database.getNodeEntityDao().getLastNodeUpdate()
  }

  override suspend fun getNodesUpdatedAfter(date: LocalDateTime): List<UnlinkedStoredNode> {
    return database.getNodeEntityDao().getNodesUpdatedAfter(date).map { it.toUnlinkedStoredNode() }
  }
}

actual fun getCommonDatabase(): ILocalDatabase {
  return NonJsLocalDatabase
}
