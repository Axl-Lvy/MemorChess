package proj.ankichess.axl.core.intf.data

import kotlinx.coroutines.flow.Flow

interface ICommonDataBase {
  fun getAllPositions(): Flow<IStoredPosition>

  suspend fun deletePosition(fen: String)

  suspend fun insertPosition(position: IStoredPosition)
}

expect fun getCommonDataBase(): ICommonDataBase
