package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDateTime

open class NoOpDatabaseQueryManager : DatabaseQueryManager {
  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<StoredNode> {
    return emptyList()
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): StoredNode? {
    return null
  }

  override suspend fun deletePosition(position: PositionIdentifier) {
    // Nothing to do
  }

  override suspend fun deleteMove(origin: PositionIdentifier, move: String) {
    // Nothing to do
  }

  override suspend fun deleteAll(hardFrom: LocalDateTime?) {
    // Nothing to do
  }

  override suspend fun insertNodes(vararg positions: StoredNode) {
    // Nothing to do
  }

  override suspend fun getLastUpdate(): LocalDateTime? {
    return null
  }

  override fun isActive(): Boolean {
    return false
  }
}
