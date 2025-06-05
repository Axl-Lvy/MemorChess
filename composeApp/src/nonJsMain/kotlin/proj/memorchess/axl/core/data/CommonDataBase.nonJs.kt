package proj.memorchess.axl.core.data

object CommonDataBase : ICommonDataBase {

  private val database = getRoomDatabase(databaseBuilder())

  override suspend fun getAllPositions(): List<NodeWithMoves> {
    return database.getNodeEntityDao().getAll()
  }

  override suspend fun deletePosition(fen: String) {
    database.getNodeEntityDao().delete(fen)
  }

  override suspend fun deleteAllPositions() {
    database.getNodeEntityDao().deleteAll()
  }

  override suspend fun insertPosition(position: IStoredNode) {
    database.getNodeEntityDao().createNewNode(NodeWithMoves.convertToEntity(position))
  }
}

actual fun getCommonDataBase(): ICommonDataBase {
  return CommonDataBase
}
