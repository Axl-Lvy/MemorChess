package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDateTime
import proj.memorchess.axl.core.data.online.database.RemoteDatabaseQueryManager

/**
 * A composite database that queries multiple databases.
 *
 * Every get is done on the local database in priority.
 *
 * @property remoteDatabase Remote database
 * @property localDatabase Local database
 */
class CompositeDatabase(
  private val remoteDatabase: RemoteDatabaseQueryManager,
  private val localDatabase: DatabaseQueryManager,
) : DatabaseQueryManager {
  override suspend fun getAllNodes(): List<StoredNode> {
    return if (localDatabase.isActive()) {
      localDatabase.getAllNodes()
    } else {
      remoteDatabase.getAllNodes()
    }
  }

  override suspend fun getAllPositions(): List<UnlinkedStoredNode> {
    return if (localDatabase.isActive()) {
      localDatabase.getAllPositions()
    } else {
      remoteDatabase.getAllPositions()
    }
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): StoredNode? {
    return if (localDatabase.isActive()) {
      localDatabase.getPosition(positionIdentifier)
    } else {
      remoteDatabase.getPosition(positionIdentifier)
    }
  }

  override suspend fun deletePosition(fen: String) {
    for (db in listOf(localDatabase, remoteDatabase)) {
      if (db.isActive()) {
        db.deletePosition(fen)
      }
    }
  }

  override suspend fun deleteMove(origin: String, move: String) {
    for (db in listOf(localDatabase, remoteDatabase)) {
      if (db.isActive()) {
        db.deleteMove(origin, move)
      }
    }
  }

  override suspend fun insertMove(move: StoredMove) {
    for (db in listOf(localDatabase, remoteDatabase)) {
      if (db.isActive()) {
        db.insertMove(move)
      }
    }
  }

  override suspend fun getAllMoves(withDeletedOnes: Boolean): List<StoredMove> {
    return if (localDatabase.isActive()) {
      localDatabase.getAllMoves(withDeletedOnes)
    } else {
      remoteDatabase.getAllMoves(withDeletedOnes)
    }
  }

  override suspend fun deleteAll(hardFrom: LocalDateTime?) {
    for (db in listOf(localDatabase, remoteDatabase)) {
      if (db.isActive()) {
        db.deleteAll(hardFrom)
      }
    }
  }

  override suspend fun insertPosition(position: StoredNode) {
    for (db in listOf(localDatabase, remoteDatabase)) {
      if (db.isActive()) {
        db.insertPosition(position)
      }
    }
  }

  override suspend fun getLastUpdate(): LocalDateTime? {
    val local = if (localDatabase.isActive()) localDatabase.getLastUpdate() else null
    val remote = if (remoteDatabase.isActive()) remoteDatabase.getLastUpdate() else null
    if (local != null && remote != null) {
      check(local == remote)
    }
    return local ?: remote
  }

  override fun isActive(): Boolean {
    return remoteDatabase.isActive() || localDatabase.isActive()
  }
}
