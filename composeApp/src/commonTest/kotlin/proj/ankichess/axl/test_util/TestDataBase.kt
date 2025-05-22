package proj.ankichess.axl.test_util

import proj.ankichess.axl.core.intf.data.ICommonDataBase
import proj.ankichess.axl.core.intf.data.IStoredNode

object TestDataBase : ICommonDataBase {
  val storedNodes = mutableMapOf<String, IStoredNode>()

  override suspend fun getAllPositions(): List<IStoredNode> {
    return storedNodes.values.toList()
  }

  override suspend fun deletePosition(fen: String) {
    storedNodes.remove(fen)
  }

  override suspend fun insertPosition(position: IStoredNode) {
    storedNodes.put(position.positionKey.fenRepresentation, position)
  }

  override suspend fun deleteAllPositions() {
    storedNodes.clear()
  }
}
