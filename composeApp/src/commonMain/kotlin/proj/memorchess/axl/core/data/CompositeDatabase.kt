package proj.memorchess.axl.core.data

import kotlin.time.Instant
import proj.memorchess.axl.core.data.online.database.DatabaseOperation
import proj.memorchess.axl.core.data.online.database.DatabaseUploader
import proj.memorchess.axl.core.data.online.database.SupabaseQueryManager

/**
 * A composite database that queries multiple databases.
 *
 * Every get is done on the local database in priority. Write operations are performed on the local
 * database immediately and queued for upload to the remote database asynchronously.
 *
 * User Operation │ ▼ CompositeDatabase │ ├── localDatabase.isActive()? │ │ │ Yes │ No │ │
 * ├────►Local DB (sync) + enqueue to Uploader (async) │ │ │ remoteDatabase.isActive()? │ │ │ Yes │
 * └────────►Remote DB (sync, immediate - webapp scenario)
 *
 * @property remoteDatabase Remote database
 * @property localDatabase Local database
 * @property databaseUploader Uploader that handles async remote operations
 */
class CompositeDatabase(
  private val remoteDatabase: SupabaseQueryManager,
  private val localDatabase: DatabaseQueryManager,
  private val databaseUploader: DatabaseUploader,
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
      databaseUploader.enqueue(DatabaseOperation.DeletePosition(position))
    } else if (remoteDatabase.isActive()) {
      remoteDatabase.deletePosition(position)
    }
  }

  override suspend fun deleteMove(origin: PositionIdentifier, move: String) {
    if (localDatabase.isActive()) {
      localDatabase.deleteMove(origin, move)
      databaseUploader.enqueue(DatabaseOperation.DeleteMove(origin, move))
    } else if (remoteDatabase.isActive()) {
      remoteDatabase.deleteMove(origin, move)
    }
  }

  override suspend fun deleteAll(hardFrom: Instant?) {
    if (localDatabase.isActive()) {
      localDatabase.deleteAll(hardFrom)
      databaseUploader.enqueue(DatabaseOperation.DeleteAll(hardFrom))
    } else if (remoteDatabase.isActive()) {
      remoteDatabase.deleteAll(hardFrom)
    }
  }

  override suspend fun insertNodes(vararg positions: DataNode) {
    if (localDatabase.isActive()) {
      localDatabase.insertNodes(*positions)
      databaseUploader.enqueue(DatabaseOperation.InsertNodes(positions.toList()))
    } else if (remoteDatabase.isActive()) {
      remoteDatabase.insertNodes(*positions)
    }
  }

  override suspend fun getLastUpdate(): Instant? {
    val local = if (localDatabase.isActive()) localDatabase.getLastUpdate() else null
    if (local != null) {
      return local
    }
    val remote =
      if (remoteDatabase.isActive() && databaseUploader.isSynced) remoteDatabase.getLastUpdate()
      else null
    if (remote != null) {
      return remote
    }
    return null
  }

  override fun isActive(): Boolean {
    return remoteDatabase.isActive() || localDatabase.isActive()
  }

  private fun throwNoActiveDatabaseException(): Nothing {
    throw IllegalStateException("No active database found.")
  }
}
