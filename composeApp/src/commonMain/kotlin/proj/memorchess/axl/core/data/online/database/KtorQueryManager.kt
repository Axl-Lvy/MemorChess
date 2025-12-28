package proj.memorchess.axl.core.data.online.database

import kotlin.time.Instant
import proj.memorchess.axl.core.data.DataNode
import proj.memorchess.axl.core.data.DatabaseQueryManager
import proj.memorchess.axl.core.data.PositionIdentifier

class KtorQueryManager : DatabaseQueryManager {
  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<DataNode> {
    TODO("Not yet implemented")
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): DataNode? {
    TODO("Not yet implemented")
  }

  override suspend fun deletePosition(position: PositionIdentifier) {
    TODO("Not yet implemented")
  }

  override suspend fun deleteMove(origin: PositionIdentifier, move: String) {
    TODO("Not yet implemented")
  }

  override suspend fun deleteAll(hardFrom: Instant?) {
    TODO("Not yet implemented")
  }

  override suspend fun insertNodes(vararg positions: DataNode) {
    TODO("Not yet implemented")
  }

  override suspend fun getLastUpdate(): Instant? {
    TODO("Not yet implemented")
  }

  override fun isActive(): Boolean {
    TODO("Not yet implemented")
  }
}
