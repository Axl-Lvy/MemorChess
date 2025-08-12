package proj.memorchess.axl.core.data

import kotlinx.datetime.LocalDateTime
import proj.memorchess.axl.core.data.online.database.DatabaseSynchronizer
import proj.memorchess.axl.core.data.online.database.SupabaseQueryManager
import proj.memorchess.axl.core.date.DateUtil.isAlmostEqual

/**
 * A composite database that queries multiple databases.
 *
 * Every get is done on the local database in priority.
 *
 * @property remoteDatabase Remote database
 * @property localDatabase Local database
 */
class CompositeDatabase(
  private val remoteDatabase: SupabaseQueryManager,
  private val localDatabase: DatabaseQueryManager,
  private val databaseSynchronizer: DatabaseSynchronizer,
) : DatabaseQueryManager {
  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<StoredNode> {
    return if (localDatabase.isActive()) {
      localDatabase.getAllNodes(withDeletedOnes)
    } else if (remoteDatabase.isActive()) {
      remoteDatabase.getAllNodes(withDeletedOnes)
    } else {
      throwNoActiveDatabaseException()
    }
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): StoredNode? {
    return if (localDatabase.isActive()) {
      localDatabase.getPosition(positionIdentifier)
    } else if (remoteDatabase.isActive()) {
      remoteDatabase.getPosition(positionIdentifier)
    } else {
      throwNoActiveDatabaseException()
    }
  }

  override suspend fun deletePosition(position: PositionIdentifier) {
    if (localDatabase.isActive()) {
      localDatabase.deletePosition(position)
    }
    if (remoteDatabase.isActive() && databaseSynchronizer.isSynced) {
      remoteDatabase.deletePosition(position)
    }
  }

  override suspend fun deleteMove(origin: PositionIdentifier, move: String) {
    if (localDatabase.isActive()) {
      localDatabase.deleteMove(origin, move)
    }
    if (remoteDatabase.isActive() && databaseSynchronizer.isSynced) {
      remoteDatabase.deleteMove(origin, move)
    }
  }

  override suspend fun deleteAll(hardFrom: LocalDateTime?) {
    if (localDatabase.isActive()) {
      localDatabase.deleteAll(hardFrom)
    }
    if (remoteDatabase.isActive() && databaseSynchronizer.isSynced) {
      remoteDatabase.deleteAll(hardFrom)
    }
  }

  override suspend fun insertNodes(vararg positions: StoredNode) {
    if (localDatabase.isActive()) {
      localDatabase.insertNodes(*positions)
    }
    if (remoteDatabase.isActive() && databaseSynchronizer.isSynced) {
      remoteDatabase.insertNodes(*positions)
    }
  }

  override suspend fun getLastUpdate(): LocalDateTime? {
    val local = if (localDatabase.isActive()) localDatabase.getLastUpdate() else null
    val remote =
      if (remoteDatabase.isActive() && databaseSynchronizer.isSynced) remoteDatabase.getLastUpdate()
      else null
    if (local != null && remote != null) {
      check(local.isAlmostEqual(remote))
    }
    return local ?: remote
  }

  override fun isActive(): Boolean {
    return remoteDatabase.isActive() || localDatabase.isActive()
  }

  private fun throwNoActiveDatabaseException(): Nothing {
    throw IllegalStateException("No active database found.")
  }
}
