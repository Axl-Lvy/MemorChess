package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDateTime

open class NoOpDatabaseQueryManager : DatabaseQueryManager {
  override suspend fun getAllNodes(): List<StoredNode> {
    return emptyList()
  }

  override suspend fun getAllPositions(): List<UnlinkedStoredNode> {
    return emptyList()
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): StoredNode? {
    return null
  }

  override suspend fun deletePosition(fen: String) {
    // Nothing to do
  }

  override suspend fun deleteMove(origin: String, move: String) {
    // Nothing to do
  }

  override suspend fun insertMove(move: StoredMove) {
    // Nothing to do
  }

  override suspend fun getAllMoves(withDeletedOnes: Boolean): List<StoredMove> {
    return emptyList()
  }

  override suspend fun deleteAll(hardFrom: LocalDateTime?) {
    // Nothing to do
  }

  override suspend fun insertPosition(position: StoredNode) {
    // Nothing to do
  }

  override suspend fun getLastUpdate(): LocalDateTime? {
    return null
  }

  override fun isActive(): Boolean {
    return false
  }
}
