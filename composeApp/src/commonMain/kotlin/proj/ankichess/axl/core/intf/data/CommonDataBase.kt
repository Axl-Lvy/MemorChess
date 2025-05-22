package proj.ankichess.axl.core.intf.data

interface ICommonDataBase {
  suspend fun getAllPositions(): List<IStoredNode>

  suspend fun deletePosition(fen: String)

  suspend fun deleteAllPositions()

  suspend fun insertPosition(position: IStoredNode)
}

expect fun getCommonDataBase(): ICommonDataBase
