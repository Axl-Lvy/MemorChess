package proj.memorchess.axl.core.data

import kotlin.time.Instant

/**
 * A no-operation implementation of [DatabaseQueryManager] that performs no actions and returns
 * default values.
 */
open class NoOpDatabaseQueryManager : DatabaseQueryManager {
  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<DataNode> {
    return emptyList()
  }

  override suspend fun getPosition(positionKey: PositionKey): DataNode? {
    return null
  }

  override suspend fun deletePosition(position: PositionKey) {
    // Nothing to do
  }

  override suspend fun deleteMove(origin: PositionKey, move: String) {
    // Nothing to do
  }

  override suspend fun deleteAll(hardFrom: Instant?) {
    // Nothing to do
  }

  override suspend fun insertNodes(vararg positions: DataNode) {
    // Nothing to do
  }

  override suspend fun getLastUpdate(): Instant? {
    return null
  }

  override fun isActive(): Boolean {
    return false
  }
}
