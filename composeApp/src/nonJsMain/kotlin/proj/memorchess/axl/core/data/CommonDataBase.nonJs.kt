package proj.memorchess.axl.core.data

/** Database for non-JS platforms */
object NonJsCommonDatabase : ICommonDatabase {

  private val database = getRoomDatabase(databaseBuilder())

  override suspend fun getAllPositions(): List<IStoredNode> {
    return database.getNodeEntityDao().getAll().map { it.toStoredNode() }
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

  override suspend fun deleteAllPositions() {
    database.getNodeEntityDao().deleteAll()
  }

  override suspend fun insertPosition(position: IStoredNode) {
    database.getNodeEntityDao().insertNodeAndMoves(NodeWithMoves.convertToEntity(position))
  }
}

actual fun getCommonDatabase(): ICommonDatabase {
  return NonJsCommonDatabase
}
