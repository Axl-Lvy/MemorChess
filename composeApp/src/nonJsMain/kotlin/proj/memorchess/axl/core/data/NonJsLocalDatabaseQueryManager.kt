package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil.truncateToSeconds
import proj.memorchess.axl.core.graph.DeleteMode

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

  override suspend fun deletePosition(position: PositionKey, mode: DeleteMode) {
    val dao = database.getNodeEntityDao()
    when (mode) {
      DeleteMode.HARD -> {
        dao.hardDeleteMoveFrom(position.value)
        dao.hardDeleteMoveTo(position.value)
        dao.hardDeleteNode(position.value)
      }
      DeleteMode.SOFT -> {
        dao.softDeleteNode(position.value)
        dao.softDeleteMoveFrom(position.value)
        dao.softDeleteMoveTo(position.value)
      }
    }
  }

  override suspend fun deleteMove(origin: PositionKey, move: String, mode: DeleteMode) {
    val dao = database.getNodeEntityDao()
    when (mode) {
      DeleteMode.HARD -> dao.hardDeleteMove(origin.value, move)
      DeleteMode.SOFT -> dao.softDeleteMove(origin.value, move)
    }
  }

  override suspend fun eraseAll() {
    val dao = database.getNodeEntityDao()
    dao.eraseAllMoves()
    dao.eraseAllNodes()
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
}

actual fun getPlatformSpecificLocalDatabase(): DatabaseQueryManager {
  return NonJsLocalDatabaseQueryManager
}
