package proj.ankichess.axl.core.intf.data

import proj.ankichess.axl.core.data.NodeEntity
import proj.ankichess.axl.core.data.databaseBuilder
import proj.ankichess.axl.core.data.getRoomDatabase

object CommonDataBase : ICommonDataBase {

  private val database = getRoomDatabase(databaseBuilder())

  override suspend fun getAllPositions(): List<IStoredNode> {
    return database.getPositionDao().getAll()
  }

  override suspend fun deletePosition(fen: String) {
    database.getPositionDao().delete(fen)
  }

  override suspend fun deleteAllPositions() {
    database.getPositionDao().deleteAll()
  }

  override suspend fun insertPosition(position: IStoredNode) {
    database.getPositionDao().insert(NodeEntity.convertToEntity(position))
  }
}

actual fun getCommonDataBase(): ICommonDataBase {
  return CommonDataBase
}
