package proj.ankichess.axl.core.intf.data

import kotlinx.coroutines.flow.Flow
import proj.ankichess.axl.core.data.PositionEntity
import proj.ankichess.axl.core.data.databaseBuilder
import proj.ankichess.axl.core.data.getRoomDatabase

object CommonDataBase : ICommonDataBase {

  private val database = getRoomDatabase(databaseBuilder())

  override fun getAllPositions(): Flow<IStoredPosition> {
    return database.getPositionDao().getAll()
  }

  override suspend fun deletePosition(fen: String) {
    database.getPositionDao().delete(fen)
  }

  override suspend fun insertPosition(position: IStoredPosition) {
    database.getPositionDao().insert(PositionEntity.convertToEntity(position))
  }
}

actual fun getCommonDataBase(): ICommonDataBase {
  return CommonDataBase
}
