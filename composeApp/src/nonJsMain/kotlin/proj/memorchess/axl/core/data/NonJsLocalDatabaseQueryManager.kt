package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil.truncateToSeconds

/** Database for non-JS platforms */
internal object NonJsLocalDatabaseQueryManager : DatabaseQueryManager {

  private val database = getRoomDatabase(databaseBuilder())

  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<DataNode> {
    val allNodes = database.getNodeEntityDao().getAllNodes()
    return (if (withDeletedOnes) allNodes else allNodes.filter { !it.node.isDeleted }).map {
      it.toStoredNode()
    }
  }

  override suspend fun getPosition(positionKey: PositionKey): DataNode? {
    return database.getNodeEntityDao().getNode(positionKey.value)?.toStoredNode()
  }

  override suspend fun deletePosition(position: PositionKey) {
    database.getNodeEntityDao().delete(position.value)
    database.getNodeEntityDao().removeMoveFrom(position.value)
    database.getNodeEntityDao().removeMoveTo(position.value)
  }

  override suspend fun deleteMove(origin: PositionKey, move: String) {
    database.getNodeEntityDao().removeMove(origin.value, move)
  }

  override suspend fun deleteAll(hardFrom: Instant?) {
    database.getNodeEntityDao().deleteAllNodes()
    hardFrom?.let { database.getNodeEntityDao().deleteNewerNodes(it) }
    database.getNodeEntityDao().deleteAllMoves()
    hardFrom?.let { database.getNodeEntityDao().deleteNewerMoves(it) }
  }

  override suspend fun insertNodes(vararg positions: DataNode) {
    database
      .getNodeEntityDao()
      .insertNodeAndMoves(positions.map { NodeWithMoves.convertToEntity(it) })
  }

  override suspend fun getLastUpdate(): Instant? {
    val move = database.getNodeEntityDao().getLastMoveUpdate()
    val node = database.getNodeEntityDao().getLastNodeUpdate()
    return (if (move != null && node != null) {
        move.coerceAtLeast(node)
      } else {
        move ?: node
      })
      ?.truncateToSeconds()
  }

  override fun isActive(): Boolean {
    return true
  }
}

actual fun getPlatformSpecificLocalDatabase(): DatabaseQueryManager {
  return NonJsLocalDatabaseQueryManager
}
