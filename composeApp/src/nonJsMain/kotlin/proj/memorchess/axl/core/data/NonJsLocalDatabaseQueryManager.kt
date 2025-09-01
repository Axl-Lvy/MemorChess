package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDateTime
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

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): DataNode? {
    return database.getNodeEntityDao().getNode(positionIdentifier.fenRepresentation)?.toStoredNode()
  }

  override suspend fun deletePosition(position: PositionIdentifier) {
    database.getNodeEntityDao().delete(position.fenRepresentation)
    database.getNodeEntityDao().removeMoveFrom(position.fenRepresentation)
    database.getNodeEntityDao().removeMoveTo(position.fenRepresentation)
  }

  override suspend fun deleteMove(origin: PositionIdentifier, move: String) {
    database.getNodeEntityDao().removeMove(origin.fenRepresentation, move)
  }

  override suspend fun deleteAll(hardFrom: LocalDateTime?) {
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

  override suspend fun getLastUpdate(): LocalDateTime? {
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
