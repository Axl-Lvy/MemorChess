package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.data.online.database.CloudUploader
import proj.memorchess.axl.core.data.online.database.DatabaseSynchronizer
import proj.memorchess.axl.core.data.online.database.SupabaseQueryManager

/**
 * A composite database that queries multiple databases.
 *
 * Every get is done on the local database in priority. Write operations are done to local database
 * synchronously and queued for cloud upload in the background.
 *
 * @property remoteDatabase Remote database
 * @property localDatabase Local database
 * @property databaseSynchronizer Database synchronizer
 * @property cloudUploader Cloud uploader for background operations
 */
class CompositeDatabase(
  private val remoteDatabase: SupabaseQueryManager,
  private val localDatabase: DatabaseQueryManager,
  private val databaseSynchronizer: DatabaseSynchronizer,
  private val cloudUploader: CloudUploader,
) : DatabaseQueryManager {
  override suspend fun getAllNodes(withDeletedOnes: Boolean): List<DataNode> {
    return if (localDatabase.isActive()) {
      localDatabase.getAllNodes(withDeletedOnes)
    } else if (remoteDatabase.isActive()) {
      remoteDatabase.getAllNodes(withDeletedOnes)
    } else {
      throwNoActiveDatabaseException()
    }
  }

  override suspend fun getPosition(positionIdentifier: PositionIdentifier): DataNode? {
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
      cloudUploader.queueDeletePosition(position)
    }
  }

  override suspend fun deleteMove(origin: PositionIdentifier, move: String) {
    if (localDatabase.isActive()) {
      localDatabase.deleteMove(origin, move)
    }
    if (remoteDatabase.isActive() && databaseSynchronizer.isSynced) {
      cloudUploader.queueDeleteMove(origin, move)
    }
  }

  override suspend fun deleteAll(hardFrom: Instant?) {
    if (localDatabase.isActive()) {
      localDatabase.deleteAll(hardFrom)
    }
    if (remoteDatabase.isActive() && databaseSynchronizer.isSynced) {
      cloudUploader.queueDeleteAll(hardFrom)
    }
  }

  override suspend fun insertNodes(vararg positions: DataNode) {
    if (localDatabase.isActive()) {
      localDatabase.insertNodes(*positions)
    }
    if (remoteDatabase.isActive() && databaseSynchronizer.isSynced) {
      cloudUploader.queueInsertNodes(*positions)
    }
  }

  override suspend fun getLastUpdate(): Instant? {
    val local = if (localDatabase.isActive()) localDatabase.getLastUpdate() else null
    val remote =
      if (remoteDatabase.isActive() && databaseSynchronizer.isSynced) remoteDatabase.getLastUpdate()
      else null
    return local ?: remote
  }

  override fun isActive(): Boolean {
    return remoteDatabase.isActive() || localDatabase.isActive()
  }

  private fun throwNoActiveDatabaseException(): Nothing {
    throw IllegalStateException("No active database found.")
  }
}
