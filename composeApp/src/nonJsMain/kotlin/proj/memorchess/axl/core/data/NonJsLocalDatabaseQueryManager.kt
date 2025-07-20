package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDateTime
import proj.memorchess.axl.core.date.DateUtil.truncateToSeconds

/** Database for non-JS platforms */
object NonJsLocalDatabaseQueryManager : DatabaseQueryManager {

  private val database = getRoomDatabase(databaseBuilder())

  override suspend fun getAllNodes(): List<StoredNode> {
    return database.getNodeEntityDao().getAllNodes().map { it.toStoredNode() }
  }

  override suspend fun getAllPositions(): List<UnlinkedStoredNode> {
    return database.getNodeEntityDao().getPositions().map { it.toUnlinkedStoredNode() }
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): StoredNode? {
    return database.getNodeEntityDao().getNode(positionIdentifier.fenRepresentation)?.toStoredNode()
  }

  override suspend fun deletePosition(fen: String) {
    database.getNodeEntityDao().delete(fen)
    database.getNodeEntityDao().removeMoveFrom(fen)
  }

  override suspend fun deleteMove(origin: String, move: String) {
    database.getNodeEntityDao().removeMove(origin, move)
  }

  override suspend fun insertMove(move: StoredMove) {
    database.getNodeEntityDao().insertMoves(listOf(MoveEntity.convertToEntity(move)))
  }

  override suspend fun getAllMoves(withDeletedOnes: Boolean): List<StoredMove> {
    return (if (withDeletedOnes) database.getNodeEntityDao().getAllMovesWithDeletedOnes()
      else database.getNodeEntityDao().getAllMoves())
      .map { it.toStoredMove() }
  }

  override suspend fun deleteAll(hardFrom: LocalDateTime?) {
    database.getNodeEntityDao().deleteAllNodes()
    hardFrom?.let { database.getNodeEntityDao().deleteNewerNodes(it) }
    database.getNodeEntityDao().deleteAllMoves()
    hardFrom?.let { database.getNodeEntityDao().deleteNewerMoves(it) }
  }

  override suspend fun insertPosition(position: StoredNode) {
    database.getNodeEntityDao().insertNodeAndMoves(NodeWithMoves.convertToEntity(position))
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

actual fun getLocalDatabase(): DatabaseQueryManager {
  return NonJsLocalDatabaseQueryManager
}
