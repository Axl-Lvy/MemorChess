package proj.memorchess.axl.core.data

/** Database for non-JS platforms */
object NonJsCommonDatabase : ICommonDatabase {

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
    database.getNodeEntityDao().deleteAll()
  }

  override suspend fun insertPosition(position: StoredNode) {
    database.getNodeEntityDao().insertNodeAndMoves(NodeWithMoves.convertToEntity(position))
  }
}

actual fun getCommonDatabase(): ICommonDatabase {
  return NonJsCommonDatabase
}
