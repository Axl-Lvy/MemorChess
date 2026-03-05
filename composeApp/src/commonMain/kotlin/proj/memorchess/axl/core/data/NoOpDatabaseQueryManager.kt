package proj.memorchess.axl.core.data

import kotlin.time.Instant

/**
 * A no-operation implementation of [DatabaseQueryManager] that performs no actions and returns
 * default values.
 */
open class NoOpDatabaseQueryManager : DatabaseQueryManager {
  override suspend fun getAllMoves(withDeletedOnes: Boolean): List<DataMove> {
    return emptyList()
  }

  override suspend fun getAllPositions(withDeletedOnes: Boolean): List<DataPosition> {
    return emptyList()
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): DataPosition? {
    return null
  }

  override suspend fun getMovesForPosition(positionIdentifier: PositionIdentifier): List<DataMove> {
    return emptyList()
  }

  override suspend fun deletePosition(position: PositionIdentifier, updatedAt: Instant) {
    // Nothing to do
  }

  override suspend fun deleteMove(origin: PositionIdentifier, move: String, updatedAt: Instant) {
    // Nothing to do
  }

  override suspend fun deleteAll(hardFrom: Instant?) {
    // Nothing to do
  }

  override suspend fun insertMoves(moves: List<DataMove>, positions: List<DataPosition>) {
    // Nothing to do
  }

  override suspend fun getLastUpdate(): Instant? {
    return null
  }

  override fun isActive(): Boolean {
    return false
  }
}
