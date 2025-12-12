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

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): DataNode? {
    return null
  }

  override suspend fun deletePosition(position: PositionIdentifier) {
    // Nothing to do
  }

  override suspend fun deleteMove(origin: PositionIdentifier, move: String) {
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
