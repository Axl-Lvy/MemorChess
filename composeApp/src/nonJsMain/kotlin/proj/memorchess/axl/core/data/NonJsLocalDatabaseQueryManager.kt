package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.date.DateUtil.truncateToSeconds
import proj.memorchess.axl.core.date.PreviousAndNextDate

/** Database for non-JS platforms */
internal object NonJsLocalDatabaseQueryManager : DatabaseQueryManager {

  private val database = getRoomDatabase(databaseBuilder())

  override suspend fun getAllMoves(withDeletedOnes: Boolean): List<DataMove> {
    val moves = if (withDeletedOnes) {
      database.getNodeEntityDao().getAllMovesWithDeletedOnes()
    } else {
      database.getNodeEntityDao().getAllMoves()
    }
    return moves.map { it.toStoredMove() }
  }

  override suspend fun getAllPositions(withDeletedOnes: Boolean): List<DataPosition> {
    val positions = database.getNodeEntityDao().getPositions()
    return (if (withDeletedOnes) positions else positions.filter { !it.isDeleted }).map {
      DataPosition(
        PositionIdentifier(it.fenRepresentation),
        it.depth,
        PreviousAndNextDate(it.lastTrainedDate, it.nextTrainedDate),
        it.updatedAt,
        it.isDeleted,
      )
    }
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): DataPosition? {
    val nodeWithMoves = database.getNodeEntityDao().getNode(positionIdentifier.fenRepresentation)
    return nodeWithMoves?.toDataPosition()
  }

  override suspend fun getMovesForPosition(positionIdentifier: PositionIdentifier): List<DataMove> {
    val nodeWithMoves = database.getNodeEntityDao().getNode(positionIdentifier.fenRepresentation)
    return nodeWithMoves?.toDataMoves() ?: emptyList()
  }

  override suspend fun deletePosition(position: PositionIdentifier, updatedAt: Instant) {
    database.getNodeEntityDao().delete(position.fenRepresentation)
    database.getNodeEntityDao().removeMoveFrom(position.fenRepresentation)
    database.getNodeEntityDao().removeMoveTo(position.fenRepresentation)
  }

  override suspend fun deleteMove(origin: PositionIdentifier, move: String, updatedAt: Instant) {
    database.getNodeEntityDao().removeMove(origin.fenRepresentation, move)
  }

  override suspend fun deleteAll(hardFrom: Instant?) {
    database.getNodeEntityDao().deleteAllNodes()
    hardFrom?.let { database.getNodeEntityDao().deleteNewerNodes(it) }
    database.getNodeEntityDao().deleteAllMoves()
    hardFrom?.let { database.getNodeEntityDao().deleteNewerMoves(it) }
  }

  override suspend fun insertMoves(moves: List<DataMove>, positions: List<DataPosition>) {
    database
      .getNodeEntityDao()
      .insertNodeAndMoves(NodeWithMoves.convertToEntities(moves, positions))
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
